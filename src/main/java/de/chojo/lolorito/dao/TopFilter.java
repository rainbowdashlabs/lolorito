package de.chojo.lolorito.dao;

public record TopFilter(SearchScope searchScope, SortOrder order, Boolean hq, Integer minSales, Double minPopularity, Double minInterest, Double minMarketVolume, Double minPrice, Double minAvgPrice) {
}
