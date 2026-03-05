package com.patriot.nav.service;

import com.patriot.nav.model.AccessProfile;
import com.patriot.nav.model.VehicleProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

@Service
@RequiredArgsConstructor
public class VehicleOptionsService {

    private final VehicleProfileService profileService;

    // --- PUBLIC API ---------------------------------------------------------

    public Map<String, Object> getDynamicOptions() {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("vehicle_schema", describeClass(VehicleProfile.class));
        result.put("access_schema", describeClass(AccessProfile.class));
        result.put("profile_values", collectProfileValues());

        return result;
    }

    // --- SCHEMA GENERATION --------------------------------------------------

    private Map<String, Object> describeClass(Class<?> clazz) {
        Map<String, Object> result = new LinkedHashMap<>();

        for (Field f : clazz.getDeclaredFields()) {
            Map<String, Object> fieldInfo = new LinkedHashMap<>();
            Class<?> type = f.getType();

            String mappedType = mapJavaTypeToAcceptedType(type);
            fieldInfo.put("type", mappedType);

            // Für Maps: zusätzliche Typ-Informationen hinzufügen
            if (mappedType.equals("Map")) {
                String mapTypeInfo = getMapTypeInfo(f);
                fieldInfo.put("mapType", mapTypeInfo);
            }

            // Für Listen: Typ der Elemente angeben
            if (mappedType.equals("List")) {
                String listElementType = getListElementType(f);
                fieldInfo.put("elementType", listElementType);
            }

            // Nested object
            if (mappedType.equals("Object")) {
                fieldInfo.put("fields", describeClass(type));
            }

            result.put(f.getName(), fieldInfo);
        }

        return result;
    }

    private String mapJavaTypeToAcceptedType(Class<?> type) {
        if (type.equals(String.class)) return "String";
        if (type.equals(Integer.class) || type.equals(int.class)) return "Int";
        if (type.equals(Double.class) || type.equals(double.class)) return "Double";
        if (type.equals(Float.class) || type.equals(float.class)) return "Float";
        if (type.equals(Boolean.class) || type.equals(boolean.class)) return "Boolean";
        if (Map.class.isAssignableFrom(type)) return "Map";
        if (List.class.isAssignableFrom(type)) return "List";
        return "Object";
    }

    /**
     * Extrahiert Typ-Informationen von Map-Feldern
     * z.B. "Map<String, Double>" für wayMultipliers
     */
    private String getMapTypeInfo(Field field) {
        Type genericType = field.getGenericType();
        
        if (genericType instanceof ParameterizedType) {
            ParameterizedType paramType = (ParameterizedType) genericType;
            Type[] typeArguments = paramType.getActualTypeArguments();
            
            if (typeArguments.length == 2) {
                String keyType = getTypeName(typeArguments[0]);
                String valueType = getTypeName(typeArguments[1]);
                return "Map<" + keyType + ", " + valueType + ">";
            }
        }
        
        return "Map<?, ?>";
    }

    /**
     * Extrahiert Element-Typ-Informationen von List-Feldern
     * z.B. "String" für List<String>
     */
    private String getListElementType(Field field) {
        Type genericType = field.getGenericType();
        
        if (genericType instanceof ParameterizedType) {
            ParameterizedType paramType = (ParameterizedType) genericType;
            Type[] typeArguments = paramType.getActualTypeArguments();
            
            if (typeArguments.length > 0) {
                return getTypeName(typeArguments[0]);
            }
        }
        
        return "Object";
    }

    /**
     * Konvertiert einen Type zu einem lesbaren Namen
     */
    private String getTypeName(Type type) {
        if (type instanceof Class<?>) {
            Class<?> clazz = (Class<?>) type;
            return clazz.getSimpleName();
        }
        
        return type.getTypeName();
    }

    // --- DYNAMIC VALUES FROM PROFILES ---------------------------------------

    private Map<String, Object> collectProfileValues() {
        Map<String, Object> result = new LinkedHashMap<>();

        Set<String> wayKeys = new TreeSet<>();
        Set<String> tagKeys = new TreeSet<>();
        Set<String> blocked = new TreeSet<>();

        for (String name : profileService.listAvailableProfiles()) {
            VehicleProfile p = profileService.getProfile(name);

            if (p.getWayMultipliers() != null)
                wayKeys.addAll(p.getWayMultipliers().keySet());

            if (p.getTagWeights() != null)
                tagKeys.addAll(p.getTagWeights().keySet());

            if (p.getBlockedTags() != null)
                blocked.addAll(p.getBlockedTags());
        }

        result.put("way_multipliers_keys", wayKeys);
        result.put("tag_weights_keys", tagKeys);
        result.put("blocked_tags_values", blocked);

        return result;
    }
}
