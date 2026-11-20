package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AssistantDao {

    @Query("SELECT * FROM tasks ORDER BY isCompleted ASC, createdAt DESC")
    fun observeTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks ORDER BY isCompleted ASC, createdAt DESC")
    suspend fun getAllTasksOnce(): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity): Long

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Query("SELECT * FROM tasks WHERE LOWER(title) LIKE '%' || LOWER(:query) || '%' LIMIT 1")
    suspend fun findTaskByTitle(query: String): TaskEntity?

    @Query("SELECT * FROM reminders WHERE isDismissed = 0 ORDER BY triggerAtMillis ASC")
    fun observeReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE isDismissed = 0 ORDER BY triggerAtMillis ASC")
    suspend fun getActiveRemindersOnce(): List<ReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderEntity): Long

    @Query("UPDATE reminders SET isFired = 1 WHERE id = :id")
    suspend fun markReminderFired(id: Long)

    @Query("UPDATE reminders SET isDismissed = 1 WHERE id = :id")
    suspend fun dismissReminder(id: Long)

    @Delete
    suspend fun deleteReminder(reminder: ReminderEntity)

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    suspend fun getReminderById(id: Long): ReminderEntity?

    @Query("SELECT * FROM action_logs ORDER BY timestamp DESC LIMIT 25")
    fun observeActionLogs(): Flow<List<ActionLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActionLog(log: ActionLogEntity): Long

    @Query("DELETE FROM action_logs")
    suspend fun clearActionLogs()
}
