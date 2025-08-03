/*
 * Created by Christian Schabesberger on 12.08.17.
 *
 * Copyright (C) 2018 Christian Schabesberger <chris.schabesberger@mailbox.org>
 * YoutubeTrendingExtractor.java is part of NewPipe Extractor.
 *
 * NewPipe Extractor is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe Extractor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe Extractor. If not, see <https://www.gnu.org/licenses/>.
 */

package org.schabi.newpipe.extractor.services.youtube.extractors.kiosk;

import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getJsonPostResponse;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getTextAtKey;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.prepareDesktopJsonBuilder;
import static org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonWriter;

import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.kiosk.KioskExtractor;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.localization.TimeAgoParser;
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import javax.annotation.Nonnull;

public class YoutubeTrendingExtractor extends KioskExtractor<StreamInfoItem> {

    public static final String KIOSK_ID = "Trending";

    private JsonObject initialData;

    private static final String VIDEOS_TAB_PARAMS = "4gIOGgxtb3N0X3BvcHVsYXI%3D";

    public YoutubeTrendingExtractor(final StreamingService service,
                                    final ListLinkHandler linkHandler,
                                    final String kioskId) {
        super(service, linkHandler, kioskId);
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {
        // @formatter:off
        final byte[] body = JsonWriter.string(prepareDesktopJsonBuilder(getExtractorLocalization(),
                getExtractorContentCountry())
                .value("browseId", "FEtrending")
                .value("params", VIDEOS_TAB_PARAMS)
                .done())
                .getBytes(StandardCharsets.UTF_8);
        // @formatter:on

        initialData = getJsonPostResponse("browse", body, getExtractorLocalization());
    }

    @Override
    public InfoItemsPage<StreamInfoItem> getPage(final Page page) {
        return InfoItemsPage.emptyPage();
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        final JsonObject header = initialData.getObject("header");
        String name = null;
        if (header.has("feedTabbedHeaderRenderer")) {
            name = getTextAtKey(header.getObject("feedTabbedHeaderRenderer"), "title");
        } else if (header.has("c4TabbedHeaderRenderer")) {
            name = getTextAtKey(header.getObject("c4TabbedHeaderRenderer"), "title");
        } else if (header.has("pageHeaderRenderer")) {
            name = getTextAtKey(header.getObject("pageHeaderRenderer"), "pageTitle");
        }

        if (isNullOrEmpty(name)) {
            throw new ParsingException("Could not get Trending name");
        }
        return name;
    }

    @Nonnull
    @Override
    public InfoItemsPage<StreamInfoItem> getInitialPage() throws ParsingException {
        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        final TimeAgoParser timeAgoParser = getTimeAgoParser();
        final JsonObject tab = getTrendingTab();
        final JsonObject tabContent = tab.getObject("content");
        final boolean isVideoTab = tab.getObject("endpoint").getObject("browseEndpoint")
                .getString("params", "").equals(VIDEOS_TAB_PARAMS);

        if (tabContent.has("richGridRenderer")) {
            tabContent.getObject("richGridRenderer")
                    .getArray("contents")
                    .stream()
                    .filter(JsonObject.class::isInstance)
                    .map(JsonObject.class::cast)
                    // Filter Trending shorts and Recently trending sections
                    .filter(content -> content.has("richItemRenderer"))
                    .map(content -> content.getObject("richItemRenderer")
                            .getObject("content")
                            .getObject("videoRenderer"))
                    .forEachOrdered(videoRenderer -> collector.commit(
                            new YoutubeStreamInfoItemExtractor(videoRenderer, timeAgoParser)));
        } else if (tabContent.has("sectionListRenderer")) {
            final Stream<JsonObject> shelves = tabContent.getObject("sectionListRenderer")
                    .getArray("contents")
                    .stream()
                    .filter(JsonObject.class::isInstance)
                    .map(JsonObject.class::cast)
                    .flatMap(content -> content.getObject("itemSectionRenderer")
                            .getArray("contents")
                            .stream())
                    .filter(JsonObject.class::isInstance)
                    .map(JsonObject.class::cast)
                    .map(content -> content.getObject("shelfRenderer"));

            final Stream<JsonObject> items;
            if (isVideoTab) {
                // The first shelf of the Videos tab contains the normal trends
                items = shelves.findFirst().stream();
            } else {
                // Filter Trending shorts and Recently trending sections which have a title,
                // contrary to normal trends
                items = shelves.filter(shelfRenderer -> !shelfRenderer.has("title"));
            }

            items.flatMap(shelfRenderer -> shelfRenderer.getObject("content")
                            .getObject("expandedShelfContentsRenderer")
                            .getArray("items")
                            .stream())
                    .filter(JsonObject.class::isInstance)
                    .map(JsonObject.class::cast)
                    .map(item -> item.getObject("videoRenderer"))
                    .forEachOrdered(videoRenderer -> collector.commit(
                            new YoutubeStreamInfoItemExtractor(videoRenderer, timeAgoParser)));
        }

        return new InfoItemsPage<>(collector, null);
    }

    private JsonObject getTrendingTab() throws ParsingException {
        return initialData.getObject("contents")
                .getObject("twoColumnBrowseResultsRenderer")
                .getArray("tabs")
                .stream()
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                .map(tab -> tab.getObject("tabRenderer"))
                .filter(tabRenderer -> tabRenderer.getBoolean("selected"))
                .filter(tabRenderer -> tabRenderer.has("content"))
                // There should be at most one tab selected
                .findFirst()
                .orElseThrow(() ->
                        new ParsingException("Could not get \"Now\" or \"Videos\" trending tab"));
    }
}
