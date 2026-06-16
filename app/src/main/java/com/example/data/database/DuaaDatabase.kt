package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [DuaaEntity::class, AppSettingsEntity::class, DuaaTriggerLogEntity::class],
    version = 7,
    exportSchema = false
)
abstract class DuaaDatabase : RoomDatabase() {
    abstract fun duaaDao(): DuaaDao

    companion object {
        @Volatile
        private var INSTANCE: DuaaDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): DuaaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DuaaDatabase::class.java,
                    "duaa_database"
                )
                .addCallback(DuaaDatabaseCallback(scope))
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DuaaDatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    populateDatabase(database.duaaDao())
                }
            }
        }

        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    val dao = database.duaaDao()
                    if (dao.getAllDuaasOnce().isEmpty()) {
                        populateDatabase(dao)
                    } else if (dao.getAppSettings() == null) {
                        dao.saveAppSettings(AppSettingsEntity())
                    }
                }
            }
        }

        private suspend fun populateDatabase(duaaDao: DuaaDao) {
            val defaultDuaas = listOf(
                DuaaEntity(
                    id = 1,
                    name = "Дуа при пробуждении",
                    description = "Транскрипция:\nАльхамду лилляхиль-лязи ахьяна ба’да ма аматана ва илейхин-нушур\n\nПеревод:\nХвала Аллаху, Который оживил нас после того, как умертвил нас, и к Которому предстоит возвращение.",
                    triggerType = "ALARM_DISMISSED",
                    soundResName = "wakeup"
                ),
                DuaaEntity(
                    id = 2,
                    name = "Дуа при выходе из дома",
                    description = "Транскрипция:\nБисмилляхи, таваккальту ’аляллахи, ва ля хауля ва ля куввата илля биллях\n\nПеревод:\nС именем Аллаха, уповаю на Аллаха, нет силы и могущества, кроме как у Аллаха.",
                    triggerType = "LEAVE_HOME",
                    soundResName = "leave_home"
                ),
                DuaaEntity(
                    id = 3,
                    name = "Дуа при входе в дом",
                    description = "Транскрипция:\nБисмилляхи валяджна, ва бисмилляхи хараджна, ва ’аля Раббина таваккальна\n\nПеревод:\nС именем Аллаха мы вошли, с именем Аллаха мы вышли и на Господа нашего уповаем.",
                    triggerType = "ENTER_HOME",
                    soundResName = "enter_home"
                ),
                DuaaEntity(
                    id = 4,
                    name = "Дуа при входе в автомобиль",
                    description = "Транскрипция:\nСубханаль-лязи саххара ляна хаза ва ма кунна ляху мукринина ва инна иля Раббина лямункалибун\n\nПеревод:\nПречист Тот, Кто подчинил нам это, ведь мы сами не были бы способны на это. Воистину, мы возвращаемся к нашему Господу.",
                    triggerType = "CAR_CONNECT",
                    soundResName = "in_car"
                ),
                DuaaEntity(
                    id = 5,
                    name = "Дуа при выходе из автомобиля",
                    description = "Транскрипция:\nАльхамдулилляхиль-лязи кафана ва аваня ва акрамана ва ’алейхи таваккальна\n\nПеревод:\nХвала Аллаху, Который избавил нас от нужды, дал нам приют, оказал почет и на Которого мы уповаем.",
                    triggerType = "CAR_DISCONNECT",
                    soundResName = "car_disconnect",
                    isHidden = true
                )
            )
            duaaDao.insertDuaas(defaultDuaas)
            duaaDao.saveAppSettings(AppSettingsEntity())
        }
    }
}
