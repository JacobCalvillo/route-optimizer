package com.routeopt.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;

/** A single delivery stop, from raw text through to resolved coordinates. */
@Entity
@Table(name = "delivery_order")
public class DeliveryOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The snippet of user input this order came from, kept for traceability. */
    @Column(length = 2000)
    private String rawText;

    private String customerName;

    /** Address exactly as the user wrote it. */
    @Column(length = 500)
    private String rawAddress;

    /** Address as returned by the geocoder, once resolved. */
    @Column(length = 500)
    private String normalizedAddress;

    private Double lat;
    private Double lon;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority = Priority.NORMAL;

    private LocalTime timeFrom;
    private LocalTime timeTo;

    /** Minutes spent at the stop itself, added to the schedule after arrival. */
    private Integer serviceMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GeocodeStatus geocodeStatus = GeocodeStatus.PENDING;

    @Column(length = 1000)
    private String notes;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public boolean isRoutable() {
        return geocodeStatus == GeocodeStatus.OK && lat != null && lon != null;
    }

    public Coordinate coordinate() {
        return isRoutable() ? new Coordinate(lat, lon) : null;
    }

    public TimeWindow timeWindow() {
        return new TimeWindow(timeFrom, timeTo);
    }

    public void setTimeWindow(TimeWindow window) {
        this.timeFrom = window == null ? null : window.from();
        this.timeTo = window == null ? null : window.to();
    }

    /** Clears any previously resolved coordinates so the address can be geocoded again. */
    public void resetGeocoding() {
        this.lat = null;
        this.lon = null;
        this.normalizedAddress = null;
        this.geocodeStatus = GeocodeStatus.PENDING;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getRawAddress() {
        return rawAddress;
    }

    public void setRawAddress(String rawAddress) {
        this.rawAddress = rawAddress;
    }

    public String getNormalizedAddress() {
        return normalizedAddress;
    }

    public void setNormalizedAddress(String normalizedAddress) {
        this.normalizedAddress = normalizedAddress;
    }

    public Double getLat() {
        return lat;
    }

    public void setLat(Double lat) {
        this.lat = lat;
    }

    public Double getLon() {
        return lon;
    }

    public void setLon(Double lon) {
        this.lon = lon;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority == null ? Priority.NORMAL : priority;
    }

    public LocalTime getTimeFrom() {
        return timeFrom;
    }

    public void setTimeFrom(LocalTime timeFrom) {
        this.timeFrom = timeFrom;
    }

    public LocalTime getTimeTo() {
        return timeTo;
    }

    public void setTimeTo(LocalTime timeTo) {
        this.timeTo = timeTo;
    }

    public Integer getServiceMinutes() {
        return serviceMinutes;
    }

    public void setServiceMinutes(Integer serviceMinutes) {
        this.serviceMinutes = serviceMinutes;
    }

    public GeocodeStatus getGeocodeStatus() {
        return geocodeStatus;
    }

    public void setGeocodeStatus(GeocodeStatus geocodeStatus) {
        this.geocodeStatus = geocodeStatus;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
