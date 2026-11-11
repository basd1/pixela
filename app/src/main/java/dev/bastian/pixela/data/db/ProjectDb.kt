package dev.bastian.pixela.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import dev.bastian.pixela.domain.ProjectSummary
import kotlinx.coroutines.flow.Flow

/** The project index: file contents live in the project JSON, this row only lists and locates them. */
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val size: Int,
    val modifiedAt: Long,
    val projectPath: String,
    val thumbnailPath: String,
)

fun ProjectEntity.toSummary() = ProjectSummary(id, name, size, modifiedAt, thumbnailPath)

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY modifiedAt DESC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun get(id: String): ProjectEntity?

    @Upsert
    suspend fun upsert(entity: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(entities = [ProjectEntity::class], version = 1, exportSchema = true)
abstract class PixelaDatabase : RoomDatabase() {
    abstract fun projects(): ProjectDao
}
