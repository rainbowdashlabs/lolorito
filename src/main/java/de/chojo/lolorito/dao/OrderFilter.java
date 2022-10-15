package de.chojo.lolorito.dao;

public class OrderFilter {

    private int minUnitPrice = 1000;
    private int minProfitPercentage = 10;
    private int minRefreshDays = 1;
    private int minPopularity = 0;
    private int minMarketVolume = 0;
    private int minInterest = 0;
    private int minSales = 0;
    private int minViews = 0;
    private Target target = Target.DATA_CENTER;

    public int minUnitPrice() {
        return minUnitPrice;
    }

    public String minRefreshDays() {
        return "%d DAYS".formatted(minRefreshDays);
    }

    public Target target() {
        return target;
    }

    public void minUnitPrice(int minUnitPrice) {
        this.minUnitPrice = minUnitPrice;
    }

    public double minProfitPercentage() {
        return minProfitPercentage / 100.0 + 1;
    }

    public void minProfitPercentage(int minProfitPercentage) {
        this.minProfitPercentage = minProfitPercentage;
    }

    public void minRefreshDays(int minRefreshDays) {
        this.minRefreshDays = minRefreshDays;
    }

    public int minPopularity() {
        return minPopularity;
    }

    public void minPopularity(int minPopularity) {
        this.minPopularity = minPopularity;
    }

    public int minMarketVolume() {
        return minMarketVolume;
    }

    public void minMarketVolume(int minMarketVolume) {
        this.minMarketVolume = minMarketVolume;
    }

    public int minInterest() {
        return minInterest;
    }

    public void minInterest(int minInterest) {
        this.minInterest = minInterest;
    }

    public int minSales() {
        return minSales;
    }

    public void minSales(int minSales) {
        this.minSales = minSales;
    }

    public int minViews() {
        return minViews;
    }

    public void minViews(int minViews) {
        this.minViews = minViews;
    }

    public void target(Target target) {
        this.target = target;
    }

    public enum Target{
        REGION("region_name"), DATA_CENTER("data_center");

        private final String columnName;

        Target(String columnName) {
            this.columnName = columnName;
        }

        public String columnName() {
            return columnName;
        }
    }
}
