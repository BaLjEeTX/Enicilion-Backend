package com.enicilion.backend.applications.service;

public class VehicleClassifier {

    public static String detectVehicleClass(String make, String model, String modifications, String engineSpec) {
        String safeMake = make != null ? make : "";
        String safeModel = model != null ? model : "";
        String safeMods = modifications != null ? modifications : "";
        String safeEngine = engineSpec != null ? engineSpec : "";

        String hay = String.format("%s %s %s %s", safeMake, safeModel, safeMods, safeEngine).toLowerCase();

        // JDM Matching regex
        if (hay.matches(".*\\b(supra|skyline|silvia|rx-7|rx7|180sx|s15|s13|gt-r|gtr|evo|lancer evolution)\\b.*")) {
            return "JDM";
        }
        // Euro Matching regex
        if (hay.matches(".*\\b(bmw|mercedes|audi|porsche|volkswagen|vw|mini)\\b.*")) {
            return "Euro";
        }
        // Modification keywords matching
        if (hay.matches(".*\\b(stage|turbo|swap|widebody|kit|stance|drift|supercharger|forged|roll cage)\\b.*")) {
            return "Modified";
        }
        return "Stock";
    }
}
