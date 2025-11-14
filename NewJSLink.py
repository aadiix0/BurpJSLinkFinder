#
#  BurpLinkFinder - Find links within JS files.
#
#  Copyright (c) 2022 Frans Hendrik Botes
#  Credit to https://github.com/GerbenJavado/LinkFinder for the idea and regex
#
from burp import IBurpExtender, IScannerCheck, IScanIssue, ITab, IContextMenuFactory
from java.io import PrintWriter
from java.net import URL
from java.util import ArrayList, List
from java.util.regex import Matcher, Pattern
import binascii
import base64
import re
import cgi
from os import path
from javax import swing
from java.awt import Font, Color, BorderLayout
from java.awt.event import MouseAdapter
from threading import Thread, Lock
#from array import array
from jarray import array
from java.awt import EventQueue
from java.lang import Runnable
from thread import start_new_thread
from javax.swing import JFileChooser
from javax.swing import JScrollPane
from javax.swing import JSplitPane
from javax.swing import JTabbedPane
from javax.swing import JTable
from javax.swing import JPanel
from javax.swing import JLabel
from javax.swing.event import DocumentListener
from javax.swing import JCheckBox
from javax.swing import SwingUtilities
from javax.swing import Timer
from javax.swing import JTextField
from javax.swing.table import AbstractTableModel
import urlparse,threading
import json
from datetime import datetime
try:
    import queue
except ImportError:
    import Queue as queue

from javax.swing.table import DefaultTableCellRenderer

class BadgeRenderer(DefaultTableCellRenderer):
    def __init__(self):
        self.setOpaque(True)

    def getTableCellRendererComponent(self, table, value, isSelected, hasFocus, row, column):
        super(BadgeRenderer, self).getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)

        if column == 1 and isinstance(value, int) and value > 0:
            self.setText("+" + str(value))
            self.setBackground(Color(16, 185, 129, 30))
            self.setForeground(Color(5, 150, 105))
            self.setBorder(swing.BorderFactory.createEmptyBorder(2, 8, 2, 8))
        else:
            self.setText(str(value))
            self.setBackground(table.getBackground())
            self.setForeground(table.getForeground())
            self.setBorder(swing.BorderFactory.createEmptyBorder(2, 8, 2, 8))

        return self

class TreeCellRenderer(DefaultTableCellRenderer):
    def getTableCellRendererComponent(self, table, value, isSelected, hasFocus, row, column):
        super(TreeCellRenderer, self).getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)

        row_obj = table.getModel().getRow(row)
        if isinstance(row_obj, ParentRow):
            if row_obj.expanded:
                self.setText(u"\u25BC") # Down-pointing triangle
            else:
                self.setText(u"\u25B6") # Right-pointing triangle
            self.setBorder(swing.BorderFactory.createEmptyBorder(0, 5, 0, 0))
        elif isinstance(row_obj, ChildRow):
            self.setText("")
            self.setBorder(swing.BorderFactory.createEmptyBorder(0, 20, 0, 0))

        return self

class BlacklistTableModel(AbstractTableModel):
    def __init__(self):
        self.data = []
        self.column_names = ["Pattern/URL", "Type", "Actions"]

    def getRowCount(self):
        return len(self.data)

    def getColumnCount(self):
        return len(self.column_names)

    def getColumnName(self, column_index):
        return self.column_names[column_index]

    def getValueAt(self, row_index, column_index):
        return self.data[row_index][column_index]

    def setData(self, new_data):
        self.data = new_data
        self.fireTableDataChanged()

class TreeTableModel(AbstractTableModel):
    def __init__(self, data):
        self.data = data
        self.column_names = ["", "JS File URL", "New Endpoints", "Status"]

    def getRowCount(self):
        count = 0
        for parent in self.data:
            count += 1
            if parent.expanded:
                count += len(parent.children)
        return count

    def getColumnCount(self):
        return len(self.column_names)

    def getColumnName(self, column_index):
        return self.column_names[column_index]

    def getValueAt(self, row_index, column_index):
        row = self.getRow(row_index)
        if isinstance(row, ParentRow):
            if column_index == 0:
                return ""
            elif column_index == 1:
                return row.url
            elif column_index == 2:
                return row.new_endpoints_count
            elif column_index == 3:
                return row.status
        elif isinstance(row, ChildRow):
            if column_index == 1:
                return row.endpoint
            elif column_index == 2:
                return row.type
            elif column_index == 3:
                return row.method
        return ""

    def getRow(self, row_index):
        current_index = 0
        for parent in self.data:
            if current_index == row_index:
                return parent
            current_index += 1
            if parent.expanded:
                if row_index < current_index + len(parent.children):
                    return parent.children[row_index - current_index]
                current_index += len(parent.children)
        return None

    def setData(self, new_data):
        self.data = new_data
        self.fireTableDataChanged()

class Row(object):
    pass

class ParentRow(Row):
    def __init__(self, url, new_endpoints_count, status, endpoints):
        self.url = url
        self.new_endpoints_count = new_endpoints_count
        self.status = status
        self.children = [ChildRow(e['endpoint']) for e in endpoints]
        self.expanded = False

class ChildRow(Row):
    def __init__(self, endpoint):
        self.endpoint = endpoint
        self.type = self.get_endpoint_type(endpoint)
        self.method = "" # Not available in current data

    def get_endpoint_type(self, endpoint):
        if re.search(r'/api/', endpoint):
            return "API"
        elif endpoint.startswith(('http:', 'https:')):
            return "Absolute URL"
        elif endpoint.startswith(('../', './')):
            return "Relative Path"
        elif re.search(r'\.(json|xml|txt|pdf|zip)$', endpoint):
            return "File"
        else:
            return "Path"

# Using the Runnable class for thread-safety with Swing
class Run(Runnable):
    def __init__(self, runner):
        self.runner = runner

    def run(self):
        self.runner()

# Needed params
JSExclusionList = ['jquery', 'google-analytics','gpt.js','modernizr','gtm','fbevents']

class BurpExtender(IBurpExtender, IScannerCheck, ITab, IContextMenuFactory):

    class MaxSizeListener(DocumentListener):
        def __init__(self, extender):
            self.extender = extender

        def insertUpdate(self, e):
            self.extender._blacklist["max_file_size_kb"] = int(self.extender.maxSizeField.getText())
            self.extender.save_blacklist()

        def removeUpdate(self, e):
            try:
                self.extender._blacklist["max_file_size_kb"] = int(self.extender.maxSizeField.getText())
                self.extender.save_blacklist()
            except ValueError:
                pass # Ignore if the field is empty

        def changedUpdate(self, e):
            pass

    class TableMouseListener(MouseAdapter):
        def __init__(self, extender):
            self.extender = extender

        def mouseClicked(self, event):
            row_index = self.extender.table.rowAtPoint(event.getPoint())
            col_index = self.extender.table.columnAtPoint(event.getPoint())

            row = self.extender.tableModel.getRow(row_index)
            if isinstance(row, ParentRow):
                if col_index == 0: # Expand/collapse column
                    row.expanded = not row.expanded
                    self.extender.tableModel.fireTableDataChanged()
                elif col_index == 2: # "New Endpoints" column
                    url = row.url
                    with self.extender.lock:
                        data = self.extender._data[url]
                        new_endpoints = [e for e in data["current_endpoints"] if e.get("status") == "new"]

                    if new_endpoints:
                        self.extender.toggle_details_card(row_index, url, new_endpoints)

    def toggle_details_card(self, row, url, new_endpoints):
        if self.details_card_panel.isVisible():
            self.details_card_panel.setVisible(False)
            return

        self.details_card_panel.removeAll()
        self.details_card_panel.setBackground(Color.WHITE)
        self.details_card_panel.setBorder(swing.BorderFactory.createLineBorder(Color(16, 185, 129), 3))

        # Header
        header_panel = JPanel(BorderLayout())
        header_label = JLabel(" New Endpoints Found (" + str(len(new_endpoints)) + ")")
        header_label.setFont(Font("Tahoma", Font.BOLD, 14))

        first_seen = new_endpoints[0]["first_seen"]
        time_diff = datetime.now() - datetime.strptime(first_seen, "%Y-%m-%dT%H:%M:%S.%f")
        minutes = int(time_diff.total_seconds() / 60)
        subtitle_text = "Detected " + str(minutes) + " minutes ago"

        subtitle_label = JLabel(subtitle_text)
        subtitle_label.setForeground(Color.GRAY)
        header_panel.add(header_label, BorderLayout.NORTH)
        header_panel.add(subtitle_label, BorderLayout.SOUTH)

        # Body
        body_panel = JPanel()
        body_panel.setLayout(swing.BoxLayout(body_panel, swing.BoxLayout.Y_AXIS))

        select_all_checkbox = JCheckBox("Select All")
        body_panel.add(select_all_checkbox)

        endpoint_checkboxes = []
        for endpoint in new_endpoints:
            checkbox = JCheckBox("  - " + endpoint["endpoint"])
            new_tag = JLabel("NEW")
            new_tag.setForeground(Color.WHITE)
            new_tag.setBackground(Color(16, 185, 129))
            new_tag.setOpaque(True)

            endpoint_panel = JPanel(BorderLayout())
            endpoint_panel.add(checkbox, BorderLayout.CENTER)
            endpoint_panel.add(new_tag, BorderLayout.EAST)

            endpoint_checkboxes.append(checkbox)
            body_panel.add(endpoint_panel)

        def on_select_all(e):
            for checkbox in endpoint_checkboxes:
                checkbox.setSelected(e.getSource().isSelected())

        select_all_checkbox.addActionListener(on_select_all)

        # Footer
        footer_panel = JPanel()

        def get_selected_endpoints():
            selected = []
            for i, checkbox in enumerate(endpoint_checkboxes):
                if checkbox.isSelected():
                    selected.append(new_endpoints[i])
            return selected

        mark_reviewed_btn = swing.JButton("Mark as Reviewed", actionPerformed=lambda e: self.mark_as_reviewed(url, get_selected_endpoints()))
        move_historic_btn = swing.JButton("Move to Historic", actionPerformed=lambda e: self.move_to_historic(url, get_selected_endpoints()))
        footer_panel.add(move_historic_btn)
        footer_panel.add(mark_reviewed_btn)

        # Layout
        self.details_card_panel.add(header_panel, BorderLayout.NORTH)
        self.details_card_panel.add(body_panel, BorderLayout.CENTER)
        self.details_card_panel.add(footer_panel, BorderLayout.SOUTH)

        # Animation
        self.details_card_panel.setPreferredSize(java.awt.Dimension(0, 0))
        self.details_card_panel.setVisible(True)

        def on_tick(e):
            height = self.details_card_panel.getPreferredSize().height
            if height < 150:
                self.details_card_panel.setPreferredSize(java.awt.Dimension(0, height + 10))
                self.details_card_panel.revalidate()
            else:
                e.getSource().stop()

        Timer(10, on_tick).start()

    def mark_as_reviewed(self, url, new_endpoints):
        with self.lock:
            js_data = self._data[url]
            for endpoint in new_endpoints:
                for current_endpoint in js_data["current_endpoints"]:
                    if current_endpoint["endpoint"] == endpoint["endpoint"]:
                        current_endpoint["status"] = "active"
            self.save_data_to_storage()
        SwingUtilities.invokeLater(self.update_table)

    def move_to_historic(self, url, new_endpoints):
        with self.lock:
            js_data = self._data[url]
            now = datetime.now().isoformat()
            for endpoint in new_endpoints:
                for i, current_endpoint in enumerate(js_data["current_endpoints"]):
                    if current_endpoint["endpoint"] == endpoint["endpoint"]:
                        historic_endpoint = js_data["current_endpoints"].pop(i)
                        historic_endpoint["moved_to_historic"] = now
                        historic_endpoint["reason"] = "user_archived"
                        js_data["historic_endpoints"].append(historic_endpoint)
            self.save_data_to_storage()
        SwingUtilities.invokeLater(self.update_table)

    def registerExtenderCallbacks(self, callbacks):
        self.callbacks = callbacks
        self.helpers = callbacks.getHelpers()
        callbacks.setExtensionName("NewJSLink")
        callbacks.issueAlert("NewJSLink Passive Scanner enabled")
        #stdout = PrintWriter(callbacks.getStdout(), True)
        #stderr = PrintWriter(callbacks.getStderr(), True)
        callbacks.registerScannerCheck(self)
        self.lock = Lock()
        self.threads = []
        self._data = {}
        self._blacklist = {}
        self.scanned_files_count = 0
        self.blacklisted_files_count = 0
        self.load_data_from_storage()
        self.load_blacklist()
        callbacks.registerContextMenuFactory(self)
        self.initUI()
        # customize our UI components
        callbacks.customizeUiComponent(self._parentPane)
        # add the custom tab to Burp's UI
        callbacks.addSuiteTab(self)

        callbacks.printOutput("BurpJS LinkFinder v2 loaded.")
        callbacks.printOutput("Copyright (c) 2022 Frans Hendrik Botes")

    def initUI(self):
        self._parentPane = JTabbedPane()

        # UI for Endpoints Table
        self.tableModel = TreeTableModel([])
        self.table = JTable(self.tableModel)
        self.table.getColumnModel().getColumn(0).setCellRenderer(TreeCellRenderer())
        self.table.getColumnModel().getColumn(0).setPreferredWidth(20)
        self.table.getColumnModel().getColumn(1).setPreferredWidth(400)
        self.table.getColumnModel().getColumn(2).setPreferredWidth(100)
        self.table.getColumnModel().getColumn(3).setPreferredWidth(100)
        self.table.addMouseListener(self.TableMouseListener(self))
        self.scrollPane = JScrollPane(self.table)

        # Historic toggle
        self.showHistoricCheckbox = JCheckBox("Show Historic Endpoints")
        self.showHistoricCheckbox.addActionListener(lambda e: self.update_table())

        # Main panel
        main_panel = JPanel(BorderLayout())
        main_panel.add(self.showHistoricCheckbox, BorderLayout.NORTH)
        main_panel.add(self.scrollPane, BorderLayout.CENTER)

        # Details card panel (initially hidden)
        self.details_card_panel = JPanel(BorderLayout())
        self.details_card_panel.setVisible(False)

        # Bottom buttons
        self.clearBtn = swing.JButton("Clear", actionPerformed=self.clear_table)
        self.exportBtn = swing.JButton("Export", actionPerformed=self.export_table)
        button_panel = JPanel()
        button_panel.add(self.clearBtn)
        button_panel.add(self.exportBtn)

        south_container = JPanel(BorderLayout())
        south_container.add(self.details_card_panel, BorderLayout.CENTER)
        south_container.add(button_panel, BorderLayout.SOUTH)

        main_panel.add(south_container, BorderLayout.SOUTH)

        self._parentPane.addTab("Main", main_panel)

        # UI for Blacklist Settings
        self.blacklistPanel = swing.JPanel()
        self.blacklistLabel = swing.JLabel("Blacklist Settings:")
        self.blacklistLabel.setFont(Font("Tahoma", Font.BOLD, 12))
        self.blacklistLabel.setForeground(Color(255,102,52))

        # Blacklist table
        self.blacklistTableModel = BlacklistTableModel()
        self.blacklistTable = JTable(self.blacklistTableModel)
        self.blacklistScrollPane = JScrollPane(self.blacklistTable)

        # Blacklist buttons
        self.addBlacklistRuleBtn = swing.JButton("Add Rule", actionPerformed=self.add_blacklist_rule)
        self.removeBlacklistRuleBtn = swing.JButton("Remove Selected", actionPerformed=self.remove_blacklist_rule)
        self.importBlacklistBtn = swing.JButton("Import", actionPerformed=self.import_blacklist)
        self.exportBlacklistBtn = swing.JButton("Export", actionPerformed=self.export_blacklist)

        # Predefined categories
        self.analyticsCheckbox = JCheckBox("Analytics")
        self.advertisingCheckbox = JCheckBox("Advertising")
        self.scopeCheckbox = JCheckBox("Only process files in scope")

        self.maxSizeLabel = JLabel("Max file size (KB):")
        self.maxSizeField = JTextField(10)

        # Statistics panel
        self.statsPanel = JPanel()
        self.scannedFilesLabel = JLabel("Scanned files: 0")
        self.blacklistedFilesLabel = JLabel("Blacklisted files: 0")
        self.statsPanel.add(self.scannedFilesLabel)
        self.statsPanel.add(self.blacklistedFilesLabel)

        # Layout for blacklist panel
        layout_blacklist = swing.GroupLayout(self.blacklistPanel)
        self.blacklistPanel.setLayout(layout_blacklist)
        layout_blacklist.setAutoCreateGaps(True)
        layout_blacklist.setAutoCreateContainerGaps(True)

        layout_blacklist.setHorizontalGroup(
            layout_blacklist.createParallelGroup()
            .addComponent(self.blacklistLabel)
            .addComponent(self.blacklistScrollPane)
            .addGroup(layout_blacklist.createSequentialGroup()
                .addComponent(self.addBlacklistRuleBtn)
                .addComponent(self.removeBlacklistRuleBtn)
                .addComponent(self.importBlacklistBtn)
                .addComponent(self.exportBlacklistBtn))
            .addGroup(layout_blacklist.createSequentialGroup()
                .addComponent(self.analyticsCheckbox)
                .addComponent(self.advertisingCheckbox)
                .addComponent(self.scopeCheckbox))
            .addGroup(layout_blacklist.createSequentialGroup()
                .addComponent(self.maxSizeLabel)
                .addComponent(self.maxSizeField))
            .addComponent(self.statsPanel)
        )

        layout_blacklist.setVerticalGroup(
            layout_blacklist.createSequentialGroup()
            .addComponent(self.blacklistLabel)
            .addComponent(self.blacklistScrollPane)
            .addGroup(layout_blacklist.createParallelGroup(swing.GroupLayout.Alignment.BASELINE)
                .addComponent(self.addBlacklistRuleBtn)
                .addComponent(self.removeBlacklistRuleBtn)
                .addComponent(self.importBlacklistBtn)
                .addComponent(self.exportBlacklistBtn))
            .addGroup(layout_blacklist.createParallelGroup(swing.GroupLayout.Alignment.BASELINE)
                .addComponent(self.analyticsCheckbox)
                .addComponent(self.advertisingCheckbox)
                .addComponent(self.scopeCheckbox))
            .addGroup(layout_blacklist.createParallelGroup(swing.GroupLayout.Alignment.BASELINE)
                .addComponent(self.maxSizeLabel)
                .addComponent(self.maxSizeField))
            .addComponent(self.statsPanel)
        )

        self.load_blacklist_ui()
        self._parentPane.addTab("Blacklist Settings", self.blacklistPanel)


    def load_data_from_storage(self):
        with self.lock:
            stored_data = self.callbacks.loadExtensionSetting("NewJSLink_data")
            if stored_data:
                self._data = json.loads(stored_data)

    def save_data_to_storage(self):
        with self.lock:
            self.callbacks.saveExtensionSetting("NewJSLink_data", json.dumps(self._data))

    def load_blacklist(self):
        with self.lock:
            stored_blacklist = self.callbacks.loadExtensionSetting("NewJSLink_blacklist")
            if stored_blacklist:
                self._blacklist = json.loads(stored_blacklist)
            else:
                # Default blacklist
                self._blacklist = {
                    "exact_urls": [],
                    "patterns": ["/google-analytics/", "/tracking*.js"],
                    "domains": ["cdn.jsdelivr.net", "cdnjs.cloudflare.com"],
                    "max_file_size_kb": 1000,
                    "auto_categories": ["analytics", "advertising"]
                }

    def save_blacklist(self):
        with self.lock:
            self.callbacks.saveExtensionSetting("NewJSLink_blacklist", json.dumps(self._blacklist))

    def getTabCaption(self):
        return "NewJSLink"
    def getUiComponent(self):
        return self._parentPane

    def load_blacklist_ui(self):
        with self.lock:
            table_data = []
            for url in self._blacklist.get("exact_urls", []):
                table_data.append([url, "Exact URL", "Remove"])
            for pattern in self._blacklist.get("patterns", []):
                table_data.append([pattern, "Pattern", "Remove"])
            for domain in self._blacklist.get("domains", []):
                table_data.append([domain, "Domain", "Remove"])

        self.blacklistTableModel.setData(table_data)
        self.analyticsCheckbox.setSelected("analytics" in self._blacklist.get("auto_categories", []))
        self.advertisingCheckbox.setSelected("advertising" in self._blacklist.get("auto_categories", []))
        self.maxSizeField.setText(str(self._blacklist.get("max_file_size_kb", 1000)))

        self.analyticsCheckbox.addActionListener(self.save_blacklist_categories)
        self.advertisingCheckbox.addActionListener(self.save_blacklist_categories)
        self.maxSizeField.getDocument().addDocumentListener(self.MaxSizeListener(self))


    def save_blacklist_categories(self, event):
        with self.lock:
            if self.analyticsCheckbox.isSelected():
                if "analytics" not in self._blacklist.get("auto_categories", []):
                    self._blacklist.get("auto_categories", []).append("analytics")
            else:
                if "analytics" in self._blacklist.get("auto_categories", []):
                    self._blacklist.get("auto_categories", []).remove("analytics")

            if self.advertisingCheckbox.isSelected():
                if "advertising" not in self._blacklist.get("auto_categories", []):
                    self._blacklist.get("auto_categories", []).append("advertising")
            else:
                if "advertising" in self._blacklist.get("auto_categories", []):
                    self._blacklist.get("auto_categories", []).remove("advertising")

        self.save_blacklist()

    def add_blacklist_rule(self, event):
        rule = swing.JOptionPane.showInputDialog(self.blacklistPanel, "Enter rule:")
        rule_type = swing.JOptionPane.showInputDialog(self.blacklistPanel, "Enter type (Exact URL, Pattern, Domain):")

        if rule and rule_type:
            with self.lock:
                if rule_type == "Exact URL":
                    self._blacklist.get("exact_urls", []).append(rule)
                elif rule_type == "Pattern":
                    self._blacklist.get("patterns", []).append(rule)
                elif rule_type == "Domain":
                    self._blacklist.get("domains", []).append(rule)

            self.save_blacklist()
            self.load_blacklist_ui()

    def remove_blacklist_rule(self, event):
        row = self.blacklistTable.getSelectedRow()
        if row != -1:
            rule = self.blacklistTableModel.getValueAt(row, 0)
            rule_type = self.blacklistTableModel.getValueAt(row, 1)

            with self.lock:
                if rule_type == "Exact URL":
                    self._blacklist.get("exact_urls", []).remove(rule)
                elif rule_type == "Pattern":
                    self._blacklist.get("patterns", []).remove(rule)
                elif rule_type == "Domain":
                    self._blacklist.get("domains", []).remove(rule)

            self.save_blacklist()
            self.load_blacklist_ui()

    def update_stats(self):
        self.scannedFilesLabel.setText("Scanned files: " + str(self.scanned_files_count))
        self.blacklistedFilesLabel.setText("Blacklisted files: " + str(self.blacklisted_files_count))

    def import_blacklist(self, event):
        chooseFile = JFileChooser()
        ret = chooseFile.showDialog(self.blacklistPanel, "Choose file")
        filename = chooseFile.getSelectedFile().getCanonicalPath()
        with open(filename, 'r') as f:
            with self.lock:
                self._blacklist = json.load(f)
        self.save_blacklist()
        self.load_blacklist_ui()

    def export_blacklist(self, event):
        chooseFile = JFileChooser()
        ret = chooseFile.showDialog(self.blacklistPanel, "Choose file")
        filename = chooseFile.getSelectedFile().getCanonicalPath()
        with open(filename, 'w') as f:
            with self.lock:
                json.dump(self._blacklist, f)

    def update_table(self):
        with self.lock:
            parent_rows = []
            show_historic = self.showHistoricCheckbox.isSelected()

            for url, data in self._data.items():
                new_endpoints_count = len([e for e in data["current_endpoints"] if e.get("status") == "new"])

                endpoints = data["current_endpoints"]
                if show_historic:
                    endpoints.extend(data["historic_endpoints"])

                parent_rows.append(ParentRow(url, new_endpoints_count, "Active", endpoints))

        self.tableModel.setData(parent_rows)

    def createMenuItems(self, invocation):
        self.context = invocation
        menuList = ArrayList()

        # Get the selected messages
        messages = invocation.getSelectedMessages()

        # Menu for JS files
        if messages:
            for messageInfo in messages:
                url = messageInfo.getUrl()
                if str(url).endswith(".js"):
                    menuList.add(swing.JMenuItem("Add to Blacklist", actionPerformed=self.add_to_blacklist))
                    return menuList

        # Menu for table rows
        row = self.table.getSelectedRow()
        if row != -1:
            status = self.tableModel.getValueAt(row, 2)
            if status == "Historic":
                menuList.add(swing.JMenuItem("Restore from Historic", actionPerformed=lambda e: self.restore_from_historic(row)))

        return menuList if menuList.size() > 0 else None

    def add_to_blacklist(self, event):
        messages = self.context.getSelectedMessages()
        with self.lock:
            for messageInfo in messages:
                url = str(messageInfo.getUrl())
                if url not in self._blacklist["exact_urls"]:
                    self._blacklist["exact_urls"].append(url)
        self.save_blacklist()
        self.load_blacklist_ui()

    def restore_from_historic(self, row):
        endpoint_to_restore = self.tableModel.getValueAt(row, 0)

        # Find the historic endpoint and move it back to current
        with self.lock:
            for url, data in self._data.items():
                for i, historic_endpoint in enumerate(data["historic_endpoints"]):
                    if historic_endpoint["endpoint"] == endpoint_to_restore:
                        restored_endpoint = data["historic_endpoints"].pop(i)
                        restored_endpoint["status"] = "active"
                        del restored_endpoint["moved_to_historic"]
                        del restored_endpoint["reason"]
                        data["current_endpoints"].append(restored_endpoint)
                        break
            self.save_data_to_storage()
        SwingUtilities.invokeLater(self.update_table)

    def clear_table(self, event):
        self.tableModel.setData([])

    def export_table(self, event):
        chooseFile = JFileChooser()
        ret = chooseFile.showDialog(self._parentPane, "Choose file")
        if ret == JFileChooser.APPROVE_OPTION:
            filename = chooseFile.getSelectedFile().getCanonicalPath()
            with open(filename, 'w') as f:
                for row_index in range(self.tableModel.getRowCount()):
                    row = self.tableModel.getRow(row_index)
                    if isinstance(row, ParentRow):
                        f.write(row.url + '\n')
                        for child in row.children:
                            f.write('\t' + child.endpoint + '\n')

    def is_blacklisted(self, url_str, response_length):
        with self.lock:
            # Check exact URLs
            if url_str in self._blacklist.get("exact_urls", []):
                return True

            # Check patterns
            for pattern in self._blacklist.get("patterns", []):
                if re.search(pattern, url_str):
                    return True

            # Check domains
            domain = urlparse.urlparse(url_str).hostname
            if domain in self._blacklist.get("domains", []):
                return True

            # Check file size
            max_size_kb = self._blacklist.get("max_file_size_kb", 1000)
            if (response_length / 1024) > max_size_kb:
                return True

            # Check categories
            if "analytics" in self._blacklist.get("auto_categories", []) and any(x in url_str for x in ['google-analytics', 'mixpanel']):
                return True
            if "advertising" in self._blacklist.get("auto_categories", []) and any(x in url_str for x in ['ads', 'pixel']):
                return True

        return False

    def doPassiveScan(self, ihrr):
        try:
            urlReq = ihrr.getUrl()
            urlStr = str(urlReq)
            self.callbacks.printOutput("doPassiveScan called for URL: " + urlStr)

            if ".js" in urlStr:
                self.callbacks.printOutput("JS file detected: " + urlStr)
                if self.scopeCheckbox.isSelected() and not self.callbacks.isInScope(urlReq):
                    return None

                self.scanned_files_count += 1
                if self.is_blacklisted(urlStr, len(ihrr.getResponse())):
                    self.blacklisted_files_count += 1
                    self.callbacks.printOutput("\n" + "[-] URL blacklisted " + urlStr)
                    SwingUtilities.invokeLater(self.update_stats)
                    return None

                self.callbacks.printOutput("\n" + "[+] Valid URL found: " + urlStr)

                with self.lock:
                    if urlStr not in self._data:
                        self._data[urlStr] = {
                            "current_endpoints": [],
                            "historic_endpoints": []
                        }
                    js_data = self._data[urlStr]

                    previous_endpoints = [e["endpoint"] for e in js_data["current_endpoints"]]
                    historic_endpoints = [e["endpoint"] for e in js_data["historic_endpoints"]]

                linkA = linkAnalyse(ihrr, self.callbacks, self.helpers)
                endpoints = linkA.analyseURL()

                new_endpoints = []
                full_urls = []
                highlights = []

                if endpoints:
                    for endpoint in endpoints:
                        full_url = endpoint['link']
                        if not linkA.valcheckFullURL(full_url):
                            full_url = urlparse.urljoin(urlparse.urljoin(urlStr, '/'), full_url)

                        if full_url not in full_urls:
                            full_urls.append(full_url)

                        if full_url not in previous_endpoints and full_url not in historic_endpoints:
                            new_endpoints.append(full_url)

                        lh = [endpoint['start'], endpoint['end']]
                        if lh not in highlights:
                            highlights.append(lh)

                    with self.lock:
                        now = datetime.now().isoformat()
                        for endpoint in full_urls:
                            if not any(e['endpoint'] == endpoint for e in js_data['current_endpoints']):
                                js_data['current_endpoints'].append({
                                    "endpoint": endpoint,
                                    "first_seen": now,
                                    "status": "new"
                                })

                        self._data[urlStr] = js_data
                        self.save_data_to_storage()

                    self.callbacks.printOutput("Found {} endpoints in {}".format(len(full_urls), urlStr))
                    self.callbacks.printOutput("Adding row to table: " + urlStr)
                    SwingUtilities.invokeLater(self.update_table)

                    if full_urls:
                        issues = ArrayList()
                        issues.add(SRI(ihrr, self.helpers, self.callbacks, [e['link'] for e in endpoints], full_urls, highlights))
                        return issues
        except UnicodeEncodeError:
            self.callbacks.printOutput("Error in URL decode.")

        return None

    def consolidateDuplicateIssues(self, isb, isa):
        return -1
    def extensionUnloaded(self):
        self.callbacks.printOutput("BurpJS LinkFinder v2 unloaded")
        return

    def URL_SPLITTER(self,url):
        URL_SPLIT = str(url).split("://",1)
        URL_PROTOCAL = URL_SPLIT[0]
        if URL_PROTOCAL == 'https':
            URL_PORT = 443
        elif URL_PROTOCAL == 'http':
            URL_PORT = 80
        else:
            URL_PORT = 443
        URL_HOSTNAME = URL_SPLIT[1].split('/',1)[0].split('?',1)[0]
        if ':' in URL_HOSTNAME:
            URL_HOSTNAME_FOR_SPLIT = URL_HOSTNAME
            URL_HOSTNAME = URL_HOSTNAME_FOR_SPLIT.split(':')[0]
            URL_PORT = int(URL_HOSTNAME_FOR_SPLIT.split(':')[1])
        URL_HOST_FULL = URL_PROTOCAL+"://"+URL_HOSTNAME
        try:
            URL_HOST_SERVICE = self.helpers.buildHttpService(URL_HOSTNAME,URL_PORT,URL_PROTOCAL)
        except java.lang.IllegalArgumentException:
            self.callbacks.printOutput("EXCEPTION BECAUSE HTTPSERVICE VALUES IS INVALID : {} : ".format(url))
            self.callbacks.printOutput("EXCEPTION VALUES ARE :",URL_HOSTNAME,URL_PORT,URL_PROTOCAL)
        return URL_SPLIT,URL_PROTOCAL,URL_HOSTNAME,URL_PORT,URL_HOST_FULL,URL_HOST_SERVICE

class linkAnalyse():

    def __init__(self, reqres, callbacks, helpers):
        self.callbacks = callbacks
        self.helpers = helpers
        self.reqres = reqres


    regex_str = """

      (?:"|')                               # Start newline delimiter

      (
        ((?:[a-zA-Z]{1,10}://|//)           # Match a scheme [a-Z]*1-10 or //
        [^"'/]{1,}\.                        # Match a domainname (any character + dot)
        [a-zA-Z]{2,}[^"']{0,})              # The domainextension and/or path

        |

        ((?:/|\.\./|\./)                    # Start with /,../,./
        [^"'><,;| *()(%%$^/\\\[\]]          # Next character can't be...
        [^"'><,;|()]{1,})                   # Rest of the characters can't be

        |

        ([a-zA-Z0-9_\-/]{1,}/               # Relative endpoint with /
        [a-zA-Z0-9_\-/.]{1,}                # Resource name
        \.(?:[a-zA-Z]{1,4}|action)          # Rest + extension (length 1-4 or action)
        (?:[\?|/][^"|']{0,}|))              # ? mark with parameters

        |

        ([a-zA-Z0-9_\-/]{1,}/               # REST API (no extension) with /
        [a-zA-Z0-9_\-/]{3,}                 # Proper REST endpoints usually have 3+ chars
        (?:[\?|#][^"|']{0,}|))              # ? or # mark with parameters

        |

        ([a-zA-Z0-9_\-]{1,}                 # filename
        \.(?:php|asp|aspx|jsp|json|
             action|html|js|txt|xml)        # . + extension
        (?:\?[^"|']{0,}|))                  # ? mark with parameters

      )

      (?:"|')                               # End newline delimiter

    """

    def parser_file(self, content, regex_str, mode=1, more_regex=None, no_dup=1):
        #print ("TEST parselfile #2")
        regex = re.compile(regex_str, re.VERBOSE)
        items = [{"link": m.group(1),"start":m.start(1),"end":m.end(1)} for m in re.finditer(regex, content)]
        if no_dup:
            # Remove duplication
            all_links = set()
            no_dup_items = []
            for item in items:
                if item["link"] not in all_links:
                    all_links.add(item["link"])
                    no_dup_items.append(item)
            items = no_dup_items

        # Match Regex
        filtered_items = []
        for item in items:
            # Remove other capture groups from regex results
            if more_regex:
                if re.search(more_regex, item["link"]):
                    #print ("TEST parselfile #3")
                    filtered_items.append(item)
            else:
                filtered_items.append(item)
        return filtered_items
    # Potential for use in the future...
    def threadAnalysis(self):
        thread = Thread(target=self.analyseURL(), args=(session,))
        thread.daemon = True
        thread.start()

    def analyseURL(self):
        endpoints = ""
        mime_type=self.helpers.analyzeResponse(self.reqres.getResponse()).getStatedMimeType()
        if mime_type.lower() == 'script':
                url = self.reqres.getUrl()
                encoded_resp=binascii.b2a_base64(self.reqres.getResponse())
                decoded_resp=base64.b64decode(encoded_resp)
                endpoints=self.parser_file(decoded_resp, self.regex_str)
                return endpoints
        return endpoints

    def checkValidFile(self,fileNam):
        regexFile = """^[a-zA-Z0-9](?:[a-zA-Z0-9 ._-]*[a-zA-Z0-9])?.[a-zA-Z0-9_-][.].*"""
        try:
            if fileNam and fileNam.strip():
                return bool(re.search(regexFile, fileNam))

        except:


            return False

    def isNotBlank(self,myString):
        try:
            if myString and myString.strip():
                #myString is not None AND myString is not empty or blank
                return True
            #myString is None OR myString is empty or blank
        except:
            return False

    def valcheckMappedList(self,myString,mapTxtArea):
        #Checks if the extracted URL is a full URL or if already in the mapped list
        #print("Checking URL: " + myString)
        try:
            if (myString in mapTxtArea.text):
                #print("Found HTTP in URL: " + myString)
                return False

        except Exception as e:
            self.callbacks.printOutput(myString + "\t" + str(e))
            return True

        #print("Returning Default: " + myString)
        return True

    def valcheckFullURL(self,myString):
        try:
            if (myString[:4].lower() == 'http'):
                return True
        except Exception as e:
            self.callbacks.printOutput(myString + "\t" + str(e))
        return False

class SRI(IScanIssue,ITab):
    def __init__(self, reqres, helpers, callbacks, links, full_urls, highlights):
        self.helpers = helpers
        self.callbacks = callbacks

        self.links = links
        self.links.sort()
        self.full_urls = full_urls
        self.full_urls.sort()

        al = ArrayList()
        i=0
        while i<len(highlights):
            al.add(array([highlights[i][0],highlights[i][1]],'i'))
            i+=1
        self.highlights = al
        self.reqres = self.callbacks.applyMarkers(reqres,None,self.highlights)

        self.issue_detail = "Burp Scanner has analysed this JS file and has discovered the following link values: <ul>\n"
        i=0
        while i<len(self.links):
            self.issue_detail += "<li>{}</li>\n".format(cgi.escape(self.links[i]))
            i+=1
        self.issue_detail += "</ul>The following full normalized URLs were generated from the discovered link values: <ul>\n"
        i=0
        while i<len(self.full_urls):
            self.issue_detail += "<li>{}</li>\n".format(cgi.escape(self.full_urls[i]))
            i+=1
        self.issue_detail = str(self.issue_detail)

    def getHost(self):
        return self.reqres.getHost()

    def getPort(self):
        return self.reqres.getPort()

    def getProtocol(self):
        return self.reqres.getProtocol()

    def getUrl(self):
        return self.reqres.getUrl()

    def getIssueName(self):
        return "Linkfinder Analysed JS files"

    def getIssueType(self):
        return 0x08000000  # See http:#portswigger.net/burp/help/scanner_issuetypes.html

    def getSeverity(self):
        return "Information"  # "High", "Medium", "Low", "Information" or "False positive"

    def getConfidence(self):
        return "Certain"  # "Certain", "Firm" or "Tentative"

    def getIssueBackground(self):
        return str("JS files holds links to other parts of web applications. Refer to TAB for results.")

    def getRemediationBackground(self):
        return None

    def getIssueDetail(self):
        return self.issue_detail

    def getRemediationDetail(self):
        return None

    def getHttpMessages(self):
        #print ("................raising issue................")
        rra = [self.reqres]
        return rra

    def getHttpService(self):
        return self.reqres.getHttpService()
