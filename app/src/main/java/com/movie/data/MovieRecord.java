package com.movie.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Java port of MovieRecord.kt
 *
 * Kotlin data class MovieRecord(
 *     @PrimaryKey(autoGenerate = true) val id: Int = 0,
 *     val name: String,
 *     val sublink: String = "",
 *     val category: String = "",
 *     val link: String = "",
 *     val pageUrl: String = "",
 *     val importedAt: Long = System.currentTimeMillis()
 * )
 */
@Entity(tableName = "movies")
public class MovieRecord {

    @PrimaryKey(autoGenerate = true)
    private int id;

    @NonNull
    private String name;

    @NonNull
    private String sublink;

    @NonNull
    private String category;

    @NonNull
    private String link;

    @NonNull
    private String pageUrl;

    private long importedAt;

    @Ignore // <--- ROOM-AI INTHA CONSTRUCTOR-AI IGNORE PANNA SOLLALAM
    public MovieRecord() {
        this(0, "", "", "", "", "", System.currentTimeMillis());
    }

    @Ignore // <--- ADD THIS
    public MovieRecord(@NonNull String name) {
        this(0, name, "", "", "", "", System.currentTimeMillis());
    }

    @Ignore // <--- ADD THIS
    public MovieRecord(int id,
                       @NonNull String name,
                       @NonNull String sublink,
                       @NonNull String category,
                       @NonNull String link,
                       @NonNull String pageUrl) {
        this(id, name, sublink, category, link, pageUrl, System.currentTimeMillis());
    }

    public MovieRecord(int id,
                       @NonNull String name,
                       @NonNull String sublink,
                       @NonNull String category,
                       @NonNull String link,
                       @NonNull String pageUrl,
                       long importedAt) {
        this.id = id;
        this.name = name == null ? "" : name;
        this.sublink = sublink == null ? "" : sublink;
        this.category = category == null ? "" : category;
        this.link = link == null ? "" : link;
        this.pageUrl = pageUrl == null ? "" : pageUrl;
        this.importedAt = importedAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    @NonNull
    public String getName() {
        return name;
    }

    public void setName(@NonNull String name) {
        this.name = name == null ? "" : name;
    }

    @NonNull
    public String getSublink() {
        return sublink;
    }

    public void setSublink(@NonNull String sublink) {
        this.sublink = sublink == null ? "" : sublink;
    }

    @NonNull
    public String getCategory() {
        return category;
    }

    public void setCategory(@NonNull String category) {
        this.category = category == null ? "" : category;
    }

    @NonNull
    public String getLink() {
        return link;
    }

    public void setLink(@NonNull String link) {
        this.link = link == null ? "" : link;
    }

    @NonNull
    public String getPageUrl() {
        return pageUrl;
    }

    public void setPageUrl(@NonNull String pageUrl) {
        this.pageUrl = pageUrl == null ? "" : pageUrl;
    }

    public long getImportedAt() {
        return importedAt;
    }

    public void setImportedAt(long importedAt) {
        this.importedAt = importedAt;
    }

    /** Mirrors Kotlin's structural equality of the data class. */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MovieRecord)) return false;
        MovieRecord that = (MovieRecord) o;
        return id == that.id
                && importedAt == that.importedAt
                && name.equals(that.name)
                && sublink.equals(that.sublink)
                && category.equals(that.category)
                && link.equals(that.link)
                && pageUrl.equals(that.pageUrl);
    }

    @Override
    public int hashCode() {
        int result = id;
        result = 31 * result + name.hashCode();
        result = 31 * result + sublink.hashCode();
        result = 31 * result + category.hashCode();
        result = 31 * result + link.hashCode();
        result = 31 * result + pageUrl.hashCode();
        result = 31 * result + (int) (importedAt ^ (importedAt >>> 32));
        return result;
    }

    /** Mirrors Kotlin's generated toString(). */
    @Override
    public String toString() {
        return "MovieRecord(id=" + id
                + ", name=" + name
                + ", sublink=" + sublink
                + ", category=" + category
                + ", link=" + link
                + ", pageUrl=" + pageUrl
                + ", importedAt=" + importedAt + ")";
    }
}
