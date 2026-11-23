package app.workphotolog.android

import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

data class Project(val id: String, val name: String)

data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double,
    val ageMillis: Long,
    val precision: String,
)

data class CaptureDraft(
    val file: java.io.File,
    val projectId: String,
    val takenAtMillis: Long,
    val utcOffsetMinutes: Int,
    val note: String,
    val location: LocationFix?,
    val stampJson: String? = null,
    val isVideo: Boolean = false,
)

@Entity(tableName = "photos", foreignKeys = [
    ForeignKey(
        entity = ProjectRow::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.RESTRICT,
    ),
], indices = [Index(value = ["projectId", "takenAtMillis"])])
data class Photo(
    @PrimaryKey val id: String,
    val projectId: String,
    val takenAtMillis: Long,
    val utcOffsetMinutes: Int,
    val note: String,
    @Embedded(prefix = "location_") val location: LocationFix?,
    val sha256: String,
    val stampJson: String? = null,
    @ColumnInfo(defaultValue = "0") val isVideo: Boolean = false,
)

@Entity(tableName = "projects")
internal data class ProjectRow(@PrimaryKey val id: String, val name: String) {
    fun toProject() = Project(id, name)
}

@Dao
internal interface PhotoDao {
    @Query("SELECT * FROM projects ORDER BY name COLLATE NOCASE, id")
    fun observeProjects(): Flow<List<ProjectRow>>

    @Query("SELECT * FROM photos WHERE projectId = :projectId ORDER BY takenAtMillis DESC, id")
    fun observePhotos(projectId: String): Flow<List<Photo>>

    @Query("SELECT * FROM photos WHERE projectId = :projectId ORDER BY takenAtMillis DESC, id LIMIT 1")
    fun observeRecent(projectId: String): Flow<Photo?>

    @Query("SELECT * FROM photos WHERE projectId = :projectId AND isVideo = 0 ORDER BY takenAtMillis, id")
    suspend fun projectPhotos(projectId: String): List<Photo>

    @Query("SELECT id FROM photos")
    suspend fun photoIds(): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM projects WHERE id = :projectId)")
    suspend fun hasProject(projectId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM projects WHERE name = :name COLLATE NOCASE)")
    suspend fun hasProjectName(name: String): Boolean

    @Insert
    suspend fun addProject(project: ProjectRow)

    @Insert
    suspend fun addPhoto(photo: Photo)
}

@Database(
    entities = [ProjectRow::class, Photo::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
internal abstract class PhotoDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao
}
