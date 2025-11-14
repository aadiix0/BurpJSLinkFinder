from burp.api.montoya.scanner.audit.issues import AuditIssue
from burp.api.montoya.scanner.audit.issues import AuditIssueConfidence
from burp.api.montoya.scanner.audit.issues import AuditIssueSeverity

class ScanIssue:
    @staticmethod
    def create_issue(name, detail, remediation, url, severity, confidence, request_response):
        return AuditIssue.auditIssue(
            name,
            detail,
            remediation,
            url,
            severity,
            confidence,
            None,
            None,
            severity,
            request_response
        )
