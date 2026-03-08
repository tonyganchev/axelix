/*
 * Copyright (C) 2025-2026 Axelix Labs
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package com.axelixlabs.axelix.sbs.spring.core.master;

import java.util.List;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

import com.axelixlabs.axelix.common.api.registration.BasicDiscoveryMetadata.VMFeature;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link OptionsParsingVMFeaturesProvider}.
 *
 * @author Mikhail Polivakha
 */
class OptionsParsingVMFeaturesProviderTest {

    @Test
    void returnsAppCdsDisabled_whenVmOptionsEmpty() {
        // given.
        var subject = new OptionsParsingVMFeaturesProvider(List.of());

        // when.
        List<VMFeature> features = subject.discover();

        // then.
        VMFeature appCds = findByName(features, "AppCDS");
        assertThat(appCds).isNotNull();
        assertThat(appCds.isEnabled()).isFalse();
        assertThat(appCds.getDescription())
                .contains("Application Class Data Sharing")
                .contains("shared archive");
    }

    @Test
    void returnsAppCdsEnabled_whenSharedArchiveFilePresent() {
        // given.
        var subject =
                new OptionsParsingVMFeaturesProvider(List.of("-Xmx256m", "-XX:SharedArchiveFile=/path/to/archive.jsa"));

        // when.
        List<VMFeature> features = subject.discover();

        // then.
        VMFeature appCds = findByName(features, "AppCDS");
        assertThat(appCds).isNotNull();
        assertThat(appCds.isEnabled()).isTrue();
    }

    @Test
    void returnsAppCdsDisabled_whenXshareOffPresent() {
        // given.
        var subject = new OptionsParsingVMFeaturesProvider(List.of("-Xshare:off"));

        // when.
        List<VMFeature> features = subject.discover();

        // then.
        VMFeature appCds = findByName(features, "AppCDS");
        assertThat(appCds).isNotNull();
        assertThat(appCds.isEnabled()).isFalse();
    }

    @Test
    void returnsAppCdsDisabled_whenSharedArchiveFileThenXshareOff() {
        // given.
        var subject = new OptionsParsingVMFeaturesProvider(
                List.of("-XX:SharedArchiveFile=/path/to/archive.jsa", "-Xshare:off"));

        // when.
        List<VMFeature> features = subject.discover();

        // then.
        VMFeature appCds = findByName(features, "AppCDS");
        assertThat(appCds).isNotNull();
        assertThat(appCds.isEnabled()).isFalse();
    }

    @Test
    void returnsAppCdsDisabled_whenXshareOffThenSharedArchiveFile() {
        // given.
        var subject = new OptionsParsingVMFeaturesProvider(
                List.of("-Xshare:off", "-XX:SharedArchiveFile=/path/to/archive.jsa"));

        // when.
        List<VMFeature> features = subject.discover();

        // then.
        VMFeature appCds = findByName(features, "AppCDS");
        assertThat(appCds).isNotNull();
        assertThat(appCds.isEnabled()).isFalse();
    }

    @Test
    void returnsAotCacheDisabled_whenOptionAbsent() {
        // given.
        var subject = new OptionsParsingVMFeaturesProvider(List.of());

        // when.
        List<VMFeature> features = subject.discover();

        // then.
        VMFeature aotCache = findByName(features, "AotCache");

        if (Runtime.version().feature() >= 24) {
            assertThat(aotCache).isNotNull();
            assertThat(aotCache.isEnabled()).isFalse();
        } else {
            assertThat(aotCache).isNull();
        }
    }

    @Test
    void returnsAotCacheEnabled_whenAotCacheOptionPresent() {
        // given.
        var subject = new OptionsParsingVMFeaturesProvider(List.of("-XX:AOTCache=/path/to/cache"));

        // when.
        List<VMFeature> features = subject.discover();

        // then.
        VMFeature aotCache = findByName(features, "AotCache");
        if (Runtime.version().feature() >= 24) {
            assertThat(aotCache).isNotNull();
            assertThat(aotCache.isEnabled()).isTrue();
        } else {
            assertThat(aotCache).isNull();
        }
    }

    @Test
    void returnsCompressedObjectHeadersDisabled_whenOptionAbsent() {
        // given.
        var subject = new OptionsParsingVMFeaturesProvider(List.of());

        // when.
        List<VMFeature> features = subject.discover();

        // then.
        VMFeature coh = findByName(features, "CompressedObjectHeaders");
        if (Runtime.version().feature() >= 24) {
            assertThat(coh).isNotNull();
            assertThat(coh.isEnabled()).isFalse();
        } else {
            assertThat(coh).isNull();
        }
    }

    @Test
    void returnsCompressedObjectHeadersEnabled_whenOptionPresent() {
        // given.
        var subject = new OptionsParsingVMFeaturesProvider(List.of("-XX:+UseCompactObjectHeaders"));

        // when.
        List<VMFeature> features = subject.discover();

        // then.
        VMFeature coh = findByName(features, "CompressedObjectHeaders");
        if (Runtime.version().feature() >= 24) {
            assertThat(coh).isNotNull();
            assertThat(coh.isEnabled()).isTrue();
        } else {
            assertThat(coh).isNull();
        }
    }

    private static VMFeature findByName(List<VMFeature> features, @NonNull String name) {
        return features.stream()
                .filter(f -> name.equals(f.getName()))
                .findFirst()
                .orElse(null);
    }
}
