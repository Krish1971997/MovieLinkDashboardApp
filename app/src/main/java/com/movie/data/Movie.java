package com.movie.data;

import androidx.annotation.NonNull;
import androidx.room.Ignore;

/**
 * Android-compatible port of the standalone <b>Movie</b> class.
 *
 * <p>The original desktop/console class held the five scraped columns of a single movie row.
 * It is kept as a plain POJO (no Room annotations on the entity itself) so the scraper can build
 * and merge rows before they are handed to the database layer.</p>
 *
 * <p>Column contract (unchanged from the original):
 * <ul>
 *   <li>B = name      (movie title)</li>
 *   <li>C = sublink   (/slug-of-movie/)</li>
 *   <li>D = category  (/tamil-2026-movies/)</li>
 *   <li>E = link      (direct page url)</li>
 *   <li>F = pageUrl   (category/portal url)</li>
 * </ul>
 */
public class Movie {

    private String name;
    private String sublink;
    private String category;
    private String link;
    private String pageUrl;

    @Ignore
    public Movie() {
        this("", "", "", "", "");
    }

    @Ignore
    public Movie(@NonNull String name, @NonNull String category) {
        this(name, "", category, "", "");
    }

    @Ignore
    public Movie(String name, String sublink, String category, String link, String pageUrl) {
        this.name = name == null ? "" : name.trim();
        this.sublink = sublink == null ? "" : sublink.trim();
        this.category = category == null ? "" : category.trim();
        this.link = link == null ? "" : link.trim();
        this.pageUrl = pageUrl == null ? "" : pageUrl.trim();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name.trim();
    }

    public String getSublink() {
        return sublink;
    }

    public void setSublink(String sublink) {
        this.sublink = sublink == null ? "" : sublink.trim();
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category == null ? "" : category.trim();
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link == null ? "" : link.trim();
    }

    public String getPageUrl() {
        return pageUrl;
    }

    public void setPageUrl(String pageUrl) {
        this.pageUrl = pageUrl == null ? "" : pageUrl.trim();
    }

    /** Category path is stored with a leading/trailing slash (/tamil-2026-movies/). */
    public String getCategoryKey() {
        String c = category == null ? "" : category.trim();
        if (c.startsWith("/")) c = c.substring(1);
        if (c.endsWith("/")) c = c.substring(0, c.length() - 1);
        return c;
    }

    /** Bridges the scraper model into the Room entity used by the app. */
    public MovieRecord toRecord() {
        return new MovieRecord(0, name, sublink, category, link, pageUrl);
    }

    public static Movie fromRecord(MovieRecord r) {
        if (r == null) return null;
        return new Movie(r.getName(), r.getSublink(), r.getCategory(), r.getLink(), r.getPageUrl());
    }

    public boolean isValid() {
        return name != null && !name.trim().isEmpty();
    }

    @Override
    public String toString() {
        return "Movie(name=" + name
                + ", sublink=" + sublink
                + ", category=" + category
                + ", link=" + link
                + ", pageUrl=" + pageUrl + ")";
    }
}
