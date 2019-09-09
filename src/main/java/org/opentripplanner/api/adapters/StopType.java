package org.opentripplanner.api.adapters;

import java.util.List;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.opentripplanner.model.FeedScopedId;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.opentripplanner.model.Stop;

import static org.opentripplanner.api.adapters.WheelChairCodeMapper.mapToWheelChairCode;

@XmlRootElement(name = "Stop")
public class StopType {

    public StopType() {
    }

    public StopType(Stop stop) {
        this.id = stop.getId();
        this.stopLat = stop.getLat();
        this.stopLon = stop.getLon();
        this.stopCode = stop.getCode();
        this.stopName = stop.getName();
        this.stopDesc = stop.getDescription();
        this.zoneId = stop.getZone();
        this.stopUrl = stop.getUrl();
        this.locationType = 0;
        this.parentStation = stop.getParentStation().getId().getId();
        // this.stopTimezone = stop.getTimezone();s
        this.wheelchairBoarding = mapToWheelChairCode(stop.getWheelchairBoarding());
    }

    public StopType(Stop stop, Boolean extended) {
        this.id = stop.getId();
        this.stopLat = stop.getLat();
        this.stopLon = stop.getLon();
        this.stopCode = stop.getCode();
        this.stopName = stop.getName();
        if (extended != null && extended.equals(true)) {
            this.stopDesc = stop.getDescription();
            this.zoneId = stop.getZone();
            this.stopUrl = stop.getUrl();
            this.locationType = 0;
            this.parentStation = stop.getParentStation().getId().getId();
            // this.stopTimezone = stop.getTimezone();
            this.wheelchairBoarding = mapToWheelChairCode(stop.getWheelchairBoarding());
        }
    }

    @XmlJavaTypeAdapter(AgencyAndIdAdapter.class)
    @JsonSerialize
    FeedScopedId id;

    @XmlAttribute
    @JsonSerialize
    String stopName;

    @XmlAttribute
    @JsonSerialize
    Double stopLat;

    @XmlAttribute
    @JsonSerialize
    Double stopLon;

    @XmlAttribute
    @JsonSerialize
    String stopCode;

    @XmlAttribute
    @JsonSerialize
    String stopDesc;

    @XmlAttribute
    @JsonSerialize
    String zoneId;

    @XmlAttribute
    @JsonSerialize
    String stopUrl;

    @XmlAttribute
    @JsonSerialize
    Integer locationType;

    @XmlAttribute
    @JsonSerialize
    String parentStation;

    @XmlAttribute
    @JsonSerialize
    Integer wheelchairBoarding;

    @XmlAttribute
    @JsonSerialize
    String direction;

    @XmlElements(value = @XmlElement(name = "route"))
    public List<FeedScopedId> routes;

}
