package org.mapdb;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentNavigableMap;

import static org.junit.Assert.*;

@SuppressWarnings({ "unchecked", "rawtypes" })
public class DBMakerTest{

    
    private void verifyDB(DB db) {
        Map m = db.getHashMap("test");
        m.put(1,2);
        assertEquals(2,m.get(1));
    }


    @Test
    public void testNewMemoryDB() throws Exception {
        DB db = DBMaker
                .newMemoryDB()
                .transactionDisable()
                .make();
        verifyDB(db);
    }


    @Test
    public void testNewFileDB() throws Exception {
        File f = UtilsTest.tempDbFile();
        DB db = DBMaker.newFileDB(f)
                .transactionDisable().make();
        verifyDB(db);
    }

    @Test
    public void testDisableTransactions() throws Exception {
        DBMaker.newMemoryDB().make();
    }

    @Test
    public void testDisableCache() throws Exception {
        DB db = DBMaker
                .newMemoryDB()
                .transactionDisable()
                .cacheDisable()
                .make();
        verifyDB(db);
        Store s = Store.forDB(db);
        assertEquals(s.getClass(), StoreDirect.class);
    }


    @Test
    public void testAsyncWriteEnable() throws Exception {
        DB db = DBMaker
                .newMemoryDB()
                .asyncWriteEnable()
                .make();
        verifyDB(db);
        Store store = Store.forDB(db);
        assertEquals(store.caches[0].getClass(), Store.Cache.HashTable.class);
        Engine w =  db.engine;
        //TODO reenalbe after async is finished
//        assertEquals(w.getWrappedEngine().getClass(),AsyncWriteEngine.class);
    }


    @Test
    public void testMake() throws Exception {
        DB db = DBMaker
                .newFileDB(UtilsTest.tempDbFile())
                .transactionDisable()
                .make();
        verifyDB(db);
        //check default values are set
        Engine w =  db.engine;
        Store store = Store.forDB(db);
        assertTrue(store.caches[0] instanceof Store.Cache.HashTable);
        assertEquals(1024 * 32, ((Store.Cache.HashTable) store.caches[0] ).items.length* store.caches.length);
        StoreDirect s = (StoreDirect) store;
        assertTrue(s.vol instanceof Volume.FileChannelVol);
    }

    @Test
    public void testMakeMapped() throws Exception {
        DB db = DBMaker
                .newFileDB(UtilsTest.tempDbFile())
                .transactionDisable()
                .mmapFileEnable()
                .make();
        verifyDB(db);
        //check default values are set
        Engine w = db.engine;
        Store store = Store.forDB(db);
        assertTrue(store.caches[0] instanceof Store.Cache.HashTable);
        assertEquals(1024 * 32, ((Store.Cache.HashTable) store.caches[0]).items.length * store.caches.length);
        StoreDirect s = (StoreDirect) store;
        assertTrue(s.vol instanceof Volume.MappedFileVol);
    }

    @Test
    public void testCacheHardRefEnable() throws Exception {
        DB db = DBMaker
                .newMemoryDB()
                .transactionDisable()
                .cacheHardRefEnable()
                .make();
        verifyDB(db);
        Store store = Store.forDB(db);
        assertTrue(store.caches[0].getClass() == Store.Cache.HardRef.class);
    }

    @Test
    public void testCacheWeakRefEnable() throws Exception {
        DB db = DBMaker
                .newMemoryDB()
                .transactionDisable()
                .cacheWeakRefEnable()
                .make();
        verifyDB(db);
        Store store = Store.forDB(db);
        Store.Cache cache = store.caches[0];
        assertTrue(cache.getClass() == Store.Cache.WeakSoftRef.class);
        assertTrue(((Store.Cache.WeakSoftRef)cache).useWeakRef);
    }


    @Test
    public void testCacheSoftRefEnable() throws Exception {
        DB db = DBMaker
                .newMemoryDB()
                .transactionDisable()
                .cacheSoftRefEnable()
                .make();
        verifyDB(db);
        Store store = Store.forDB(db);
        assertTrue(store.caches[0].getClass() == Store.Cache.WeakSoftRef.class);
        assertFalse(((Store.Cache.WeakSoftRef)store.caches[0]).useWeakRef);
    }

    @Test
    public void testCacheLRUEnable() throws Exception {
        DB db = DBMaker
                .newMemoryDB()
                .transactionDisable()
                .cacheLRUEnable()
                .make();
        verifyDB(db);
        Store store = Store.forDB(db);
        assertTrue(store.caches[0].getClass() == Store.Cache.LRU.class);
        db.close();
    }

    @Test
    public void testCacheSize() throws Exception {
        DB db = DBMaker
                .newMemoryDB()
                .transactionDisable()
                .cacheSize(1000)
                .make();
        verifyDB(db);
        Store store = Store.forDB(db);
        assertEquals(1024, ((Store.Cache.HashTable) store.caches[0]).items.length*store.caches.length);
    }


    @Test public void read_only() throws IOException {
        File f = UtilsTest.tempDbFile();
        DB db = DBMaker.newFileDB(f).make();
        db.close();
        db = DBMaker
                .newFileDB(f)
                .deleteFilesAfterClose()
                .readOnly()
                .make();
        assertTrue(db.engine instanceof Engine.ReadOnly);
        db.close();
    }


    @Test(expected = IllegalArgumentException.class)
    public void reopen_wrong_checksum() throws IOException {
        File f = UtilsTest.tempDbFile();
        DB db = DBMaker.newFileDB(f).make();
        db.close();
        db = DBMaker
                .newFileDB(f)
                .deleteFilesAfterClose()
                .cacheDisable()

                .checksumEnable()
                .make();
        Engine w = db.engine;
        assertTrue(w instanceof TxEngine);

        Store s = Store.forEngine(w);
        assertTrue(s.checksum);
        assertTrue(!s.compress);
        assertTrue(!s.encrypt);
        db.close();
    }


    @Test public void checksum() throws IOException {
        File f = UtilsTest.tempDbFile();
        DB db = DBMaker
                .newFileDB(f)
                .deleteFilesAfterClose()
                .cacheDisable()

                .checksumEnable()
                .make();

        Store s = Store.forDB(db);
        assertTrue(s.checksum);
        assertTrue(!s.compress);
        assertTrue(!s.encrypt);
        db.close();
    }

    @Test public void encrypt() throws IOException {
        File f = UtilsTest.tempDbFile();
        DB db = DBMaker
                .newFileDB(f)
                .deleteFilesAfterClose()
                .cacheDisable()

                .encryptionEnable("adqdqwd")
                .make();
        Store s = Store.forDB(db);
        assertTrue(!s.checksum);
        assertTrue(!s.compress);
        assertTrue(s.encrypt);
        db.close();
    }


    @Test(expected = IllegalArgumentException.class)
    public void reopen_wrong_encrypt() throws IOException {
        File f = UtilsTest.tempDbFile();
        DB db = DBMaker.newFileDB(f).make();
        db.close();
        db = DBMaker
                .newFileDB(f)
                .deleteFilesAfterClose()
                .cacheDisable()

                .encryptionEnable("adqdqwd")
                .make();
        Store s = Store.forDB(db);
        assertTrue(!s.checksum);
        assertTrue(!s.compress);
        assertTrue(s.encrypt);
        db.close();
    }


    @Test public void compress() throws IOException {
        File f = UtilsTest.tempDbFile();
        DB db = DBMaker
                .newFileDB(f)
                .deleteFilesAfterClose()
                .cacheDisable()
                .compressionEnable()
                .make();
        Store s = Store.forDB(db);
        assertTrue(!s.checksum);
        assertTrue(s.compress);
        assertTrue(!s.encrypt);
        db.close();
    }

    @Test(expected = IllegalArgumentException.class)
    public void reopen_wrong_compress() throws IOException {
        File f = UtilsTest.tempDbFile();
        DB db = DBMaker.newFileDB(f).make();
        db.close();
        db = DBMaker
                .newFileDB(f)
                .deleteFilesAfterClose()
                .cacheDisable()

                .compressionEnable()
                .make();
        Engine w = db.engine;
        assertTrue(w instanceof TxEngine);
        Store s = Store.forEngine(w);
        assertTrue(!s.checksum);
        assertTrue(s.compress);
        assertTrue(!s.encrypt);

        db.close();
    }



    @Test public void close_on_jvm_shutdown(){
        DBMaker
                .newTempFileDB()
                .closeOnJvmShutdown()
                .deleteFilesAfterClose()
                .make();
    }

    @Test public void tempTreeMap(){
        ConcurrentNavigableMap<Long,String> m = DBMaker.newTempTreeMap();
        m.put(111L,"wfjie");
        assertTrue(m.getClass().getName().contains("BTreeMap"));
    }

    @Test public void tempHashMap(){
        ConcurrentMap<Long,String> m = DBMaker.newTempHashMap();
        m.put(111L,"wfjie");
        assertTrue(m.getClass().getName().contains("HTreeMap"));
    }

    @Test public void tempHashSet(){
        Set<Long> m = DBMaker.newTempHashSet();
        m.add(111L);
        assertTrue(m.getClass().getName().contains("HTreeMap"));
    }

    @Test public void tempTreeSet(){
        NavigableSet<Long> m = DBMaker.newTempTreeSet();
        m.add(111L);
        assertTrue(m.getClass().getName().contains("BTreeMap"));
    }



    @Test public void keys_value_matches() throws IllegalAccessException {
        Class c = DBMaker.Keys.class;
        Set<Integer> s = new TreeSet<Integer>();
        for (Field f : c.getDeclaredFields()) {
            f.setAccessible(true);
            String value = (String) f.get(null);

            String expected = f.getName().replaceFirst("^[^_]+_","");
            assertEquals(expected, value);
        }
    }

    File folderDoesNotExist = new File("folder-does-not-exit/db.aaa");

    @Test(expected = DBException.VolumeIOError.class)
    public void nonExistingFolder(){
        DBMaker.newFileDB(folderDoesNotExist).make();
    }

    @Test(expected = DBException.VolumeIOError.class)
    public void nonExistingFolder3(){
        DBMaker.newFileDB(folderDoesNotExist).mmapFileEnable().make();
    }


    @Test(expected = DBException.VolumeIOError.class)
    public void nonExistingFolder2(){
        DBMaker
                .newFileDB(folderDoesNotExist)
                .snapshotEnable()
                .commitFileSyncDisable()
                .makeTxMaker();
    }

    @Test public void treeset_pump_presert(){
        List unsorted = Arrays.asList(4,7,5,12,9,10,11,0);

        NavigableSet<Integer> s = DBMaker.newMemoryDB().cacheDisable().transactionDisable().make()
                .createTreeSet("t")
                .pumpPresort(10)
                .pumpSource(unsorted.iterator())
                .make();

        assertEquals(Integer.valueOf(0),s.first());
        assertEquals(Integer.valueOf(12),s.last());
    }

    @Test public void treemap_pump_presert(){
        List unsorted = Arrays.asList(4,7,5,12,9,10,11,0);

        NavigableMap<Integer,Integer> s = DBMaker.newMemoryDB().cacheDisable().transactionDisable().make()
                .createTreeMap("t")
                .pumpPresort(10)
                .pumpSource(unsorted.iterator(), Fun.extractNoTransform())
                .make();

        assertEquals(Integer.valueOf(0),s.firstEntry().getKey());
        assertEquals(Integer.valueOf(12),s.lastEntry().getKey());
    }

    @Test public void heap_store(){
        DB db = DBMaker.newHeapDB().make();
        Engine  s = Store.forDB(db);

        assertTrue(s instanceof  StoreHeap);
    }
}
