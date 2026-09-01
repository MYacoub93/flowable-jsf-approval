package com.example.approval.converter;

import com.example.approval.entity.ExternalUser;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.convert.FacesConverter;
import org.omnifaces.converter.SelectItemsConverter;

/**
 * OmniFaces-based converter for the per-row user radios on the
 * "Group Memberships" page ({@code /group-memberships.xhtml}).
 *
 * <p>Extends OmniFaces {@link SelectItemsConverter}, so {@code getAsObject}
 * does not rebuild a stub object from the submitted wire value - it looks the
 * submitted id up among the row's {@code f:selectItem} values and returns the
 * <em>actual</em> {@link ExternalUser} row object (with username, email, ...)
 * of the currently loaded users page. Only {@code getAsString} is overridden
 * to produce the unique key ({@code ID_} - the PK of {@code FLOWABLE_USERS_VW}),
 * exactly as recommended by the OmniFaces documentation for entities without
 * a usable {@code toString()}.</p>
 *
 * <p>If the user is no longer among the rendered rows (e.g. the users page
 * was reloaded after the selection), the base converter returns {@code null}
 * and the page falls back to its "please select a user first" message.</p>
 */
@FacesConverter("externalUserConverter")
public class ExternalUserConverter extends SelectItemsConverter {

    @Override
    public String getAsString(FacesContext context, UIComponent component, Object value) {
        if (value == null) {
            return "";
        }
        String id = value instanceof ExternalUser user ? user.getId() : null;
        return id != null ? id : "";
    }
}