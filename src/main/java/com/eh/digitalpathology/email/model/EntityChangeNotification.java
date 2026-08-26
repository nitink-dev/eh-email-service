package com.eh.digitalpathology.email.model;

public record EntityChangeNotification<T>(String key, String entityType, T oldData, T newData) {}
