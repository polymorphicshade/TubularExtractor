package org.schabi.newpipe.extractor.services.peertube;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.schabi.newpipe.extractor.ServiceList.PeerTube;
import static org.schabi.newpipe.extractor.services.peertube.PeertubeLinkHandlerFactoryTestHelper.assertDoNotAcceptNonURLs;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.schabi.newpipe.extractor.InitNewPipeTest;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.services.peertube.linkHandler.PeertubeChannelLinkHandlerFactory;

/**
 * Test for {@link PeertubeChannelLinkHandlerFactory}
 */
public class PeertubeChannelLinkHandlerFactoryTest {

    private static PeertubeChannelLinkHandlerFactory linkHandler;

    @BeforeAll
    public static void setUp() {
        InitNewPipeTest.initEmpty();
        PeerTube.setInstance(new PeertubeInstance("https://peertube.stream", "PeerTube on peertube.stream"));
        linkHandler = PeertubeChannelLinkHandlerFactory.getInstance();
    }

    @Test
    public void acceptUrlTest() throws ParsingException {
        assertTrue(linkHandler.acceptUrl("https://peertube.stream/accounts/kranti@videos.squat.net"));
        assertTrue(linkHandler.acceptUrl("https://peertube.stream/a/kranti@videos.squat.net"));
        assertTrue(linkHandler.acceptUrl("https://peertube.stream/api/v1/accounts/kranti@videos.squat.net/videos"));
        assertTrue(linkHandler.acceptUrl("https://peertube.stream/video-channels/kranti_channel@videos.squat.net/videos"));
        assertTrue(linkHandler.acceptUrl("https://peertube.stream/c/kranti_channel@videos.squat.net/videos"));
        assertTrue(linkHandler.acceptUrl("https://peertube.stream/api/v1/video-channels/7682d9f2-07be-4622-862e-93ec812e2ffa"));
        assertTrue(linkHandler.acceptUrl("https://kolektiva.media/video-channels/documentary_channel"));

        assertDoNotAcceptNonURLs(linkHandler);
    }

    @Test
    public void getId() throws ParsingException {
        assertEquals("accounts/kranti@videos.squat.net",
                linkHandler.fromUrl("https://peertube.stream/accounts/kranti@videos.squat.net").getId());
        assertEquals("accounts/kranti@videos.squat.net",
                linkHandler.fromUrl("https://peertube.stream/a/kranti@videos.squat.net").getId());
        assertEquals("accounts/kranti@videos.squat.net",
                linkHandler.fromUrl("https://peertube.stream/accounts/kranti@videos.squat.net/videos").getId());
        assertEquals("accounts/kranti@videos.squat.net",
                linkHandler.fromUrl("https://peertube.stream/a/kranti@videos.squat.net/videos").getId());
        assertEquals("accounts/kranti@videos.squat.net",
                linkHandler.fromUrl("https://peertube.stream/api/v1/accounts/kranti@videos.squat.net").getId());
        assertEquals("accounts/kranti@videos.squat.net",
                linkHandler.fromUrl("https://peertube.stream/api/v1/accounts/kranti@videos.squat.net/videos").getId());

        assertEquals("video-channels/kranti_channel@videos.squat.net",
                linkHandler.fromUrl("https://peertube.stream/video-channels/kranti_channel@videos.squat.net/videos").getId());
        assertEquals("video-channels/kranti_channel@videos.squat.net",
                linkHandler.fromUrl("https://peertube.stream/c/kranti_channel@videos.squat.net/videos").getId());
        assertEquals("video-channels/kranti_channel@videos.squat.net",
                linkHandler.fromUrl("https://peertube.stream/c/kranti_channel@videos.squat.net/video-playlists").getId());
        assertEquals("video-channels/kranti_channel@videos.squat.net",
                linkHandler.fromUrl("https://peertube.stream/api/v1/video-channels/kranti_channel@videos.squat.net").getId());

        // instance URL ending with an "a": https://kolektiva.media
        assertEquals("video-channels/documentary_channel",
                linkHandler.fromUrl("https://kolektiva.media/video-channels/documentary_channel/videos").getId());
        assertEquals("video-channels/documentary_channel",
                linkHandler.fromUrl("https://kolektiva.media/c/documentary_channel/videos").getId());
        assertEquals("video-channels/documentary_channel",
                linkHandler.fromUrl("https://kolektiva.media/c/documentary_channel/video-playlists").getId());
        assertEquals("video-channels/documentary_channel",
                linkHandler.fromUrl("https://kolektiva.media/api/v1/video-channels/documentary_channel").getId());
    }

    @Test
    public void getUrl() throws ParsingException {
        assertEquals("https://peertube.stream/video-channels/kranti_channel@videos.squat.net",
                linkHandler.fromId("video-channels/kranti_channel@videos.squat.net").getUrl());
        assertEquals("https://peertube.stream/accounts/kranti@videos.squat.net",
                linkHandler.fromId("accounts/kranti@videos.squat.net").getUrl());
        assertEquals("https://peertube.stream/accounts/kranti@videos.squat.net",
                linkHandler.fromId("kranti@videos.squat.net").getUrl());
        assertEquals("https://peertube.stream/video-channels/kranti_channel@videos.squat.net",
                linkHandler.fromUrl("https://peertube.stream/api/v1/video-channels/kranti_channel@videos.squat.net").getUrl());
    }
}
