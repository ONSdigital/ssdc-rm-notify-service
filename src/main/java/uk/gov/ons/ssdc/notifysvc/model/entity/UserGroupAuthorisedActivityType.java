package uk.gov.ons.ssdc.notifysvc.model.entity;

public enum UserGroupAuthorisedActivityType {
  SUPER_USER,
  LIST_SURVEYS,
  CREATE_SURVEY,
  CREATE_PRINT_TEMPLATE,
  LIST_COLLECTION_EXERCISES,
  CREATE_COLLECTION_EXERCISE,
  ALLOW_PRINT_TEMPLATE_ON_ACTION_RULE,
  LIST_ALLOWED_PRINT_TEMPLATES_ON_ACTION_RULES,
  ALLOW_PRINT_TEMPLATE_ON_FULFILMENT,
  LIST_ALLOWED_PRINT_TEMPLATES_ON_FULFILMENTS,
  SEARCH_CASES,
  VIEW_CASE_DETAILS,
  CREATE_PRINT_ACTION_RULE,
  CREATE_FACE_TO_FACE_ACTION_RULE,
  CREATE_OUTBOUND_PHONE_ACTION_RULE,
  CREATE_DEACTIVATE_UAC_ACTION_RULE,
  LOAD_SAMPLE,
  VIEW_SAMPLE_LOAD_PROGRESS,
  DEACTIVATE_UAC,
  CREATE_CASE_REFUSAL,
  CREATE_CASE_INVALID_CASE,
  CREATE_CASE_PRINT_FULFILMENT,
  UPDATE_SAMPLE_SENSITIVE,
  LIST_PRINT_TEMPLATES
}
