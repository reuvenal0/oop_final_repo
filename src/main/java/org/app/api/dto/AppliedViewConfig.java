package org.app.api.dto;

/**
 * Validated and actually-applied view configuration.
 *
 * @param threeD whether 3D mode is active after coercion/validation
 * @param xAxis applied X axis index
 * @param yAxis applied Y axis index
 * @param zAxis applied Z axis index (meaningful only when threeD=true)
 */
public record AppliedViewConfig(boolean threeD, int xAxis, int yAxis, int zAxis) {
}

