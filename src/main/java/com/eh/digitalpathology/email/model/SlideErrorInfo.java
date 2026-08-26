package com.eh.digitalpathology.email.model;

public record SlideErrorInfo(String barcode, int errorCode, String errorMsg ) {
}
