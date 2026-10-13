package com.tourmate.itinerary;

public class ItineraryDisabledException extends IllegalStateException {
    public ItineraryDisabledException() { super("行程规划未启用，请配置DeepSeek并启用deepseek profile"); }
}
