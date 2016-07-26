package com.commercetools.sunrise.cms.contentful;

import com.contentful.java.cda.CDAArray;
import com.contentful.java.cda.CDAAsset;
import com.contentful.java.cda.CDAClient;
import com.contentful.java.cda.CDAEntry;
import com.commercetools.sunrise.cms.CmsIdentifier;
import com.commercetools.sunrise.cms.CmsService;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class ContentfulCmsService implements CmsService {

    private static final String ENTRY_TYPE = "content_type";
    private static final String ENTRY_KEY = "fields.name[match]";
    private static final String LIMIT = "limit";
    private static final String ONE = "1";
    private final CDAClient client;

    ContentfulCmsService(final CDAClient client) {
        this.client = client;
    }

    @Override
    public CompletionStage<Optional<String>> get(final List<Locale> locales, final CmsIdentifier cmsIdentifier) {
        return CompletableFuture.supplyAsync(() -> getEntry(cmsIdentifier)
                .map(entry -> getLocalizedField(locales, entry, cmsIdentifier.getFieldName()))
                .orElseGet(Optional::empty));
    }

    // TODO create models
    private Optional<CDAEntry> getEntry(final CmsIdentifier cmsIdentifier) {
        final CDAArray cdaArray = client.fetch(CDAEntry.class)
                .where(ENTRY_TYPE, cmsIdentifier.getEntryType())
                .where(ENTRY_KEY, cmsIdentifier.getEntryKey())
                .where(LIMIT, ONE)
                .all();
        if (cdaArray != null && cdaArray.items() != null && !cdaArray.items().isEmpty()) {
            CDAEntry cdaEntry = (CDAEntry) cdaArray.items().get(0);
            return Optional.of(cdaEntry);
        } else {
            return Optional.empty();
        }
    }

    Optional<String> getLocalizedField(final List<Locale> locales, final CDAEntry cdaEntry, final String fieldName) {
        final Optional<Locale> localeOptional = getFirstSupportedLocale(locales, cdaEntry, fieldName);
        final Object cdaEntryField = localeOptional.map(locale -> {
            cdaEntry.setLocale(locale.toLanguageTag());
            return cdaEntry.getField(fieldName);
        }).orElse(null);
        return getContentAccordingToFieldType(cdaEntryField);
    }

    private Optional<Locale> getFirstSupportedLocale(final List<Locale> locales,
                                                     final CDAEntry cdaEntry, final String fieldName) {
        final Map<String, Object> stringObjectMap = cdaEntry.rawFields();
        final Map<String, Object> contentMap = (Map<String, Object>) stringObjectMap.get(fieldName);
        if (contentMap == null) {
            return Optional.empty();
        }
        final Set<String> localesFromEntry = contentMap.keySet();
        return locales.stream()
                .filter(locale -> localesFromEntry.contains(locale.toLanguageTag()))
                .findFirst();
    }

    private Optional<String> getContentAccordingToFieldType(@Nullable final Object cdaEntryField) {
        // TODO arrays support
        if (cdaEntryField instanceof CDAAsset) {
            return Optional.of(((CDAAsset) cdaEntryField).url());
        } else if (cdaEntryField instanceof String) {
            return Optional.of(cdaEntryField.toString());
        } else {
            return Optional.empty();
        }
    }

    public static ContentfulCmsService of(final String spaceId, final String token) {
        // TODO create contentful-cms-config class
        final CDAClient client = CDAClient
                .builder()
                .setSpace(spaceId)
                .setToken(token)
                .build();
        return new ContentfulCmsService(client);
    }
}