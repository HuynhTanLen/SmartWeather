package com.example.dubaothoitiet;

public class DailyWeather {
    private String ngay;
    private double max;
    private double min;
    private double doAm;
    private double sucGio;
    private String icon;

    public DailyWeather(String ngay, double max, double min, double doAm, double sucGio, String icon) {
        this.ngay = ngay;
        this.max = max;
        this.min = min;
        this.doAm = doAm;
        this.sucGio = sucGio;
        this.icon = icon;
    }

    public String getNgay() { return ngay; }
    public String getTempMinText() { return (int)min + "°"; }
    public String getTempMaxText() { return (int)max + "°"; }
    public String getDoAmText() { return (int)doAm + "%"; }
    public String getSucGioText() { return (int)sucGio + " km/h"; }
    public String getIconUrl() {
        return "https://openweathermap.org/img/wn/" + icon + "@2x.png";
    }
}