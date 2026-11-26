package org.example.java26gruppblabb3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class PolygonValidator {
    private static final double CLOSURE_TOLERANCE = 0.000001;
    private static final double EPSILON = 0.0000000001;

    private PolygonValidator() {
    }

    static void validate(List<GeoPoint> boundary) {
        if (boundary == null || boundary.size() < 4) {
            throw new IllegalArgumentException("Draw a closed area with at least three corners.");
        }

        for (GeoPoint point : boundary) {
            if (point == null || !Double.isFinite(point.lat()) || !Double.isFinite(point.lng())
                    || point.lat() < -90 || point.lat() > 90 || point.lng() < -180 || point.lng() > 180) {
                throw new IllegalArgumentException("The selected area contains invalid coordinates.");
            }
        }

        GeoPoint first = boundary.getFirst();
        GeoPoint last = boundary.getLast();
        if (Math.abs(first.lat() - last.lat()) > CLOSURE_TOLERANCE
                || Math.abs(first.lng() - last.lng()) > CLOSURE_TOLERANCE) {
            throw new IllegalArgumentException("The selected area must be a closed polygon.");
        }

        List<GeoPoint> vertices = boundary.subList(0, boundary.size() - 1);
        Set<GeoPoint> distinctVertices = new HashSet<>(vertices);
        if (distinctVertices.size() < 3) {
            throw new IllegalArgumentException("The selected area must have at least three distinct corners.");
        }

        double twiceArea = 0;
        for (int i = 0; i < vertices.size(); i++) {
            GeoPoint current = vertices.get(i);
            GeoPoint next = vertices.get((i + 1) % vertices.size());
            twiceArea += current.lng() * next.lat() - next.lng() * current.lat();
        }
        if (Math.abs(twiceArea) < EPSILON) {
            throw new IllegalArgumentException("The selected area must cover a non-zero area.");
        }

        for (int i = 0; i < vertices.size(); i++) {
            for (int j = i + 1; j < vertices.size(); j++) {
                if (j == i + 1 || (i == 0 && j == vertices.size() - 1)) {
                    continue;
                }
                if (segmentsIntersect(vertices.get(i), vertices.get((i + 1) % vertices.size()),
                        vertices.get(j), vertices.get((j + 1) % vertices.size()))) {
                    throw new IllegalArgumentException("The selected area cannot cross itself.");
                }
            }
        }
    }

    private static boolean segmentsIntersect(GeoPoint a, GeoPoint b, GeoPoint c, GeoPoint d) {
        double abC = orientation(a, b, c);
        double abD = orientation(a, b, d);
        double cdA = orientation(c, d, a);
        double cdB = orientation(c, d, b);

        if (((abC > EPSILON && abD < -EPSILON) || (abC < -EPSILON && abD > EPSILON))
                && ((cdA > EPSILON && cdB < -EPSILON) || (cdA < -EPSILON && cdB > EPSILON))) {
            return true;
        }
        return (Math.abs(abC) <= EPSILON && onSegment(a, b, c))
                || (Math.abs(abD) <= EPSILON && onSegment(a, b, d))
                || (Math.abs(cdA) <= EPSILON && onSegment(c, d, a))
                || (Math.abs(cdB) <= EPSILON && onSegment(c, d, b));
    }

    private static double orientation(GeoPoint a, GeoPoint b, GeoPoint c) {
        return (b.lng() - a.lng()) * (c.lat() - a.lat()) - (b.lat() - a.lat()) * (c.lng() - a.lng());
    }

    private static boolean onSegment(GeoPoint a, GeoPoint b, GeoPoint point) {
        return point.lng() >= Math.min(a.lng(), b.lng()) - EPSILON
                && point.lng() <= Math.max(a.lng(), b.lng()) + EPSILON
                && point.lat() >= Math.min(a.lat(), b.lat()) - EPSILON
                && point.lat() <= Math.max(a.lat(), b.lat()) + EPSILON;
    }
}
