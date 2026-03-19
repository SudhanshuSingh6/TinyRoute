package com.tinyroute.models;

public enum UrlStatus {
    ACTIVE,             // working normally
    EXPIRED,
    CLICK_LIMIT_REACHED, // maxClicks has been hit
    DISABLED            //  turned off by user
}