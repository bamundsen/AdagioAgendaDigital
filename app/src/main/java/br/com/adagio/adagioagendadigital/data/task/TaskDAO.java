package br.com.adagio.adagioagendadigital.data.task;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;

import androidx.annotation.RequiresApi;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import br.com.adagio.adagioagendadigital.data.DbLayer;
import br.com.adagio.adagioagendadigital.data.priority.PriorityDAO;
import br.com.adagio.adagioagendadigital.data.task_tag.DbTaskTagStructure;
import br.com.adagio.adagioagendadigital.models.dto.task.TaskDtoCreate;
import br.com.adagio.adagioagendadigital.models.dto.task.TaskDtoRead;
import br.com.adagio.adagioagendadigital.models.entities.Priority;
import br.com.adagio.adagioagendadigital.ui.activities.main.fragments.tasks.TaskStaticValues;
import br.com.adagio.adagioagendadigital.ui.activities.main.fragments.tasks.utils.TypeListTaskManagementOrder;

public class TaskDAO {

    private static TaskDAO instance;
    private static PriorityDAO priorityDAO;
    private SQLiteDatabase db;

    private TaskDAO(Context context){
        DbLayer dbLayer = DbLayer.getInstance(context);

        db = dbLayer.getReadableDatabase();
    }

    public static TaskDAO getInstance(Context context){
        if(instance ==null && priorityDAO == null){
            instance = new TaskDAO(context);
            priorityDAO = PriorityDAO.getInstance(context);
        }

        return instance;
    }

    public List<TaskDtoRead> list(int limit, int offset, LocalDateTime day,
                                  TypeListTaskManagementOrder typeListTaskManagementOrder,
                                  boolean isToAddIfTodayIsPriority){

        List<TaskDtoRead> tasks = new ArrayList<>();
        String query = "";
        String queryTodayManagementScreen = "";

        if(day == null){
            if(typeListTaskManagementOrder == TypeListTaskManagementOrder.TODAY){
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    queryTodayManagementScreen = String.format("SELECT * FROM %s WHERE " +
                                    "date(%s) = date('%s') " +
                                    "LIMIT %s OFFSET %s"
                            ,DbTaskStructure.TABLE_NAME,
                            DbTaskStructure.Columns.INITIAL_MOMENT,
                            LocalDateTime.now().toLocalDate().toString(),
                            limit,offset
                    );
                }
            }
        } else {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                String dateToSearch = day.toLocalDate().toString();

                query = String.format("SELECT * FROM %s WHERE " +
                                "date(%s) = date('%s') " +
                                "LIMIT %s OFFSET %s"
                        ,DbTaskStructure.TABLE_NAME,
                        DbTaskStructure.Columns.INITIAL_MOMENT,
                        dateToSearch, limit,offset
                        );

            }
        }

        if(day == null){
            int quantityOfToday = 0;

            if(typeListTaskManagementOrder == TypeListTaskManagementOrder.TODAY){
                try(Cursor c = db.rawQuery(queryTodayManagementScreen, null)){
                    quantityOfToday = c.getCount();
                    if(c.moveToFirst()){

                        do {
                            TaskDtoRead task = fromCursor(c);
                            tasks.add(task);
                        }while(c.moveToNext());
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    int offsetToRest = returnOffsetOfRest(offset,isToAddIfTodayIsPriority);

                    query = String.format("SELECT * FROM %s WHERE date(%s) != date('%s') ORDER BY datetime(%s) LIMIT %s OFFSET %s;",
                            DbTaskStructure.TABLE_NAME, DbTaskStructure.Columns.INITIAL_MOMENT,
                            LocalDateTime.now().toLocalDate().toString(),
                            DbTaskStructure.Columns.INITIAL_MOMENT,
                            limit - quantityOfToday, offsetToRest);

                }
            } else {
                query = String.format("SELECT * FROM %s LIMIT %s OFFSET %s;",
                        DbTaskStructure.TABLE_NAME, limit, offset);
            }

        }


        try(Cursor c = db.rawQuery(query, null)){
            int quantityOfTasks = c.getCount();

            if(c.moveToFirst()){

                do {
                    TaskDtoRead task = fromCursor(c);
                    tasks.add(task);
                }while(c.moveToNext());
            }

            if(day == null && typeListTaskManagementOrder== TypeListTaskManagementOrder.TODAY && isToAddIfTodayIsPriority){
                TaskStaticValues.addToAuxOfRestAfterTodayMemo(quantityOfTasks);
            }

            return tasks;
        }

    }

    private int returnOffsetOfRest(int offset,boolean isToAddIfTodayIsPriority){
        if(offset ==0){
            return offset;
        } else {
            if(!isToAddIfTodayIsPriority){
                return TaskStaticValues.returnPreviousMemberOfLastFromAuxOffsetOfRestAfterTodayMemo(
                        TaskStaticValues.AUX_OFFSET_OF_REST_AFTER_TODAY
                );
            }

            return TaskStaticValues.AUX_OFFSET_OF_REST_AFTER_TODAY;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public ArrayList<Integer> returnFinishedOrUnfinishedTasksIds(LocalDateTime date,boolean finishedOrNot){
        String dateToSearch = date.toLocalDate().toString();
        ArrayList<Integer> ids = new ArrayList<>();

        String query = String.format("SELECT * FROM %s WHERE " +
                        "date(%s) = date('%s') AND %s = %s"
                ,DbTaskStructure.TABLE_NAME,
                DbTaskStructure.Columns.INITIAL_MOMENT,
                dateToSearch, DbTaskStructure.Columns.IS_FINISHED,
                finishedOrNot ? 1 : 0
        );

        try(Cursor c = db.rawQuery(query, null)){

            if(c.moveToFirst()){
                do {
                    @SuppressLint("Range") int id = c.getInt(
                            c.getColumnIndex(DbTaskStructure.Columns.ID)
                    );
                    ids.add(id);
                }while(c.moveToNext());
            }

        }

        return ids;
    }

    private  TaskDtoRead fromCursor(Cursor c){
        @SuppressLint("Range") int id = c.getInt(c.getColumnIndex(DbTaskStructure.Columns.ID));
        @SuppressLint("Range") String description = c.getString(c.getColumnIndex(DbTaskStructure.Columns.DESCRIPTION));
        @SuppressLint("Range") String initialMoment = c.getString(c.getColumnIndex(DbTaskStructure.Columns.INITIAL_MOMENT));
        @SuppressLint("Range") String limitMoment = c.getString(c.getColumnIndex(DbTaskStructure.Columns.LIMIT_MOMENT));
        @SuppressLint("Range") int isFinished = c.getInt(c.getColumnIndex(DbTaskStructure.Columns.IS_FINISHED));
        @SuppressLint("Range") int priority_id = c.getInt(c.getColumnIndex(DbTaskStructure.Columns.PRIORITY_ID));

        LocalDateTime initialMomentDateTime=null;
        LocalDateTime limitMomentDateTime=null;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
              initialMomentDateTime = LocalDateTime.parse(initialMoment);
              limitMomentDateTime = LocalDateTime.parse(limitMoment);
        }

        ArrayList<Integer> tagIds = returnTagIds(id);
        String priorityName = returnPriorityName(priority_id);

        boolean isFinishedBoolean = isFinished == 0 ? false : true;

        return new TaskDtoRead(id,description,initialMomentDateTime,limitMomentDateTime,isFinishedBoolean,priority_id,
                tagIds,priorityName);
    }

    private String returnPriorityName(int id){
        List<Priority> priorities = priorityDAO.list();
        String name = "";

        for(Priority priority : priorities){
            if(priority.getId() == id){
                name = priority.getName();
            }
        }
        return name;
    }

    public void save(TaskDtoCreate task) {
        ContentValues values = new ContentValues();
        values.put(DbTaskStructure.Columns.DESCRIPTION, task.getDescription());
        values.put(DbTaskStructure.Columns.INITIAL_MOMENT, task.getInitialMoment());
        values.put(DbTaskStructure.Columns.LIMIT_MOMENT, task.getLimitMoment());
        values.put(DbTaskStructure.Columns.IS_FINISHED, task.isFinished());
        values.put(DbTaskStructure.Columns.PRIORITY_ID, task.getPriority_id());

        long id = db.insert(DbTaskStructure.TABLE_NAME, null, values);

        insertTaskTags(id, task.getTags());
    }

    private  ArrayList<Integer> returnTagIds(Integer id){
        String query = String.format("SELECT * FROM %s WHERE %s = %s;",
                DbTaskTagStructure.TABLE_NAME,DbTaskTagStructure.Columns.TASK_ID, id);

        ArrayList<Integer> ids = new ArrayList<>();

        try(Cursor c = db.rawQuery(query, null)){

            if(c.moveToFirst()){
                do {
                    @SuppressLint("Range") int tagId = c.getInt(
                            c.getColumnIndex(DbTaskTagStructure.Columns.TAG_ID)
                    );
                    ids.add(tagId);
                }while(c.moveToNext());
            }

        }

        return new ArrayList<>(ids);
    }

    public int getQuantityOfTasks(){
        return returnQuantityOfTasks(String.format(
                "SELECT COUNT(*) FROM %s", DbTaskStructure.TABLE_NAME));
    }

    public int getQuantityOfTasksOfTheDay(LocalDateTime day) {
        String dateToSearch = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            dateToSearch = day.toLocalDate().toString();

            String query = String.format("SELECT count(*) FROM %s WHERE " +
                            "date(%s) = date('%s') "
                    ,DbTaskStructure.TABLE_NAME,
                    DbTaskStructure.Columns.INITIAL_MOMENT,
                    dateToSearch
            );

            return returnQuantityOfTasks(query);
        }

        return 0;
    }

    private int returnQuantityOfTasks(String sql){
        Cursor count = db.rawQuery(sql,null);

        count.moveToFirst();
        int quantity = count.getInt(0);

        count.close();

        return quantity;
    }

    private boolean tagAndTaskVinculationAlreadyExists(long task, int tag){
        String query = String.format("SELECT * FROM %s WHERE %s = %s and %s = %s;",
                DbTaskTagStructure.TABLE_NAME,DbTaskTagStructure.Columns.TASK_ID, task,
                                    DbTaskTagStructure.Columns.TAG_ID, tag);

        ArrayList<Integer> ids = new ArrayList<>();

        try(Cursor c = db.rawQuery(query, null)){

            if(c.moveToFirst()){
                do {
                    @SuppressLint("Range") int tagId = c.getInt(
                            c.getColumnIndex(DbTaskTagStructure.Columns.TAG_ID)
                    );
                    ids.add(tagId);
                }while(c.moveToNext());
            }

        }

        return ids.size() > 0;
    }

    private void insertTaskTags(long id, ArrayList<Integer> tags){
        for(Integer tagId: tags){
            if(!tagAndTaskVinculationAlreadyExists(id, tagId)){
                ContentValues values = new ContentValues();

                values.put(DbTaskTagStructure.Columns.TASK_ID,id);
                values.put(DbTaskTagStructure.Columns.TAG_ID, tagId);

                db.insert(DbTaskTagStructure.TABLE_NAME, null, values);
            }

        }
    }

    public TaskDtoRead get(int id){
        String query = String.format("SELECT * FROM %s WHERE %s = %s;",
                DbTaskStructure.TABLE_NAME,DbTaskStructure.Columns.ID, id);

        TaskDtoRead task = null;
        try(Cursor c = db.rawQuery(query, null)){

            if(c.moveToFirst()){
                do {
                    task = fromCursor(c);

                }while(c.moveToNext());
            }

        }

        return task;
    }

    public void delete(long id){
      db.delete(DbTaskStructure.TABLE_NAME,String.format(
              "%s = %s", DbTaskStructure.Columns.ID,
              id
      ) ,null);
    }

    private void deleteUndoneVinculation(Integer taskId, ArrayList<Integer> tagIds){
        for(Integer tagId : tagIds){
            db.delete(DbTaskTagStructure.TABLE_NAME,String.format(
                    "%s = %s and %s = %s ", DbTaskTagStructure.Columns.TASK_ID,
                    taskId, DbTaskTagStructure.Columns.TAG_ID,tagId
            ) ,null);
        }
    }

    public void update(TaskDtoCreate t, Integer id) {
        ContentValues values = new ContentValues();
        values.put(DbTaskStructure.Columns.PRIORITY_ID, t.getPriority_id());
        values.put(DbTaskStructure.Columns.IS_FINISHED,t.isFinished());
        values.put(DbTaskStructure.Columns.DESCRIPTION,t.getDescription());
        values.put(DbTaskStructure.Columns.INITIAL_MOMENT,t.getInitialMoment());
        values.put(DbTaskStructure.Columns.LIMIT_MOMENT,t.getLimitMoment());

        db.update(DbTaskStructure.TABLE_NAME,values,
                 DbTaskStructure.Columns.ID + " = ?",
                new String[] { String.valueOf(id)});

        insertTaskTags(id, t.getTags());
        ArrayList<Integer> undoneVinculations = returnUndoneVinculation(id,t.getTags());
        deleteUndoneVinculation(id, undoneVinculations);
    }

    private ArrayList<Integer> returnUndoneVinculation(Integer id, ArrayList<Integer> tagIds){
        ArrayList<Integer> undoneVinculations = new ArrayList<>();

        String query = String.format("SELECT * FROM %s WHERE %s = %s;",
                DbTaskTagStructure.TABLE_NAME,DbTaskTagStructure.Columns.TASK_ID, id);

        ArrayList<Integer> oldTags = new ArrayList<>();

        try(Cursor c = db.rawQuery(query, null)){

            if(c.moveToFirst()){
                do {
                    @SuppressLint("Range") int tagId = c.getInt(
                            c.getColumnIndex(DbTaskTagStructure.Columns.TAG_ID)
                    );
                    oldTags.add(tagId);
                }while(c.moveToNext());
            }

        }

        for(Integer oldTag:oldTags){
            if(!newTagsHaveOldTag(oldTag, tagIds)){
                undoneVinculations.add(oldTag);
            }
        }

        return new ArrayList<>(undoneVinculations);
    }

    private boolean newTagsHaveOldTag(Integer tagId, ArrayList<Integer> newTagIds){
        for(Integer id : newTagIds){
            if(id == tagId){
                return true;
            }
        }
        return false;
    }

    public void updateToFinished(TaskDtoRead task){
        finishOrNot(task.getId(), 1);
    }

    public void updateToUnfinished(TaskDtoRead task) {
        finishOrNot(task.getId(), 0);
    }

    public void updateToFinishedById(Integer id){
        finishOrNot(id, 1);
    }

    public void updateToUnfinishedById(Integer id){
        finishOrNot(id, 0);
    }

    private void finishOrNot(Integer id, int opr){
        ContentValues values = new ContentValues();
        values.put(DbTaskStructure.Columns.IS_FINISHED, opr);

        db.update(DbTaskStructure.TABLE_NAME, values,
                DbTaskStructure.Columns.ID + " = ?",
                new String[] {String.valueOf(id)});
    }

}














