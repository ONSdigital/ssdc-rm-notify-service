package uk.gov.ons.ssdc.notifysvc.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public class SmsFulfilmentEmptyResponseSuccess implements SmsFulfilmentResponse {}
