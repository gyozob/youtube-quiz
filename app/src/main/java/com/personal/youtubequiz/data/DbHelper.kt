package com.personal.youtubequiz.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Local metadata store. Images and audio live on internal storage; only their
 * paths and tags are kept here.
 */
class DbHelper(context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        // Deleting an image must not leave questions pointing at nothing.
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE images (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                tag         TEXT NOT NULL,
                image_path  TEXT NOT NULL,
                thumb_path  TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_images_tag ON images(tag)")

        db.execSQL(
            """
            CREATE TABLE questions (
                id               INTEGER PRIMARY KEY AUTOINCREMENT,
                tag              TEXT NOT NULL,
                audio_path       TEXT NOT NULL,
                correct_image_id INTEGER NOT NULL
                                 REFERENCES images(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_questions_tag ON questions(tag)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // No released schema to migrate from yet.
    }

    companion object {
        private const val NAME = "youtube_quiz.db"
        private const val VERSION = 1
    }
}
