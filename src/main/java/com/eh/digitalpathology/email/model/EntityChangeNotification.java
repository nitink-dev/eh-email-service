package com.eh.digitalpathology.email.model;

public record EntityChangeNotification<T>(String key, String entityType, String entityName, T oldData, T newData) {}
