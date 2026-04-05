output "waf_acl_arn" {
  description = "ARN del Web ACL WAF"
  value       = aws_wafv2_web_acl.principal.arn
}

output "waf_acl_id" {
  description = "ID del Web ACL WAF"
  value       = aws_wafv2_web_acl.principal.id
}
