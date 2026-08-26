package com.eh.digitalpathology.email.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties( ignoreUnknown = true )
public record EmailEnvelop(String barcode, String missingValue) {

    public EmailEnvelop ( String barcode ) {
        this( barcode, null );
    }
}
