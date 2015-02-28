import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.io.*;

public class Sample {

  public static void main(String[] args) {

/////////////////////////////////////////////////////////////////////////////
//This sample code is for executor and callable thing
    /*MyCallable task_1 = new MyCallable();
    MyCallable task_2 = new MyCallable();
    MyCallable task_3 = new MyCallable();


    ExecutorService executor = Executors.newFixedThreadPool(3);

    executor.submit(task_1);
    executor.submit(task_2);

    Future<String> future = executor.submit(task_3);

    try {
            String result = future.get();
            System.out.println(result);
    } catch (InterruptedException | ExecutionException e) {

        e.printStackTrace();
    }

    executor.shutdown();*/
///////////////////////////////////////////////////////////////////////////////
//This sample code is for reading and writing pieces
	try {
	File f = new File("peer_id1");
	if(!f.exists())
		f.mkdirs();
	RandomAccessFile fl = new RandomAccessFile("peer_id1/p1", "rw");
	RandomAccessFile fl1 = new RandomAccessFile("clean.sh", "r");
//Writing Pieces
	byte[] temp = new byte[40];
	int i = 0;
	while (i < 40){
		temp[i] = fl1.readByte();
		i++;
	}
 	i = 0;
	while (i < 40){
		fl.writeByte(temp[i]);
		i++;
	}	
//Reading Pieces
	byte[] content = new byte[10];
	int offset = 3*10; //piece index * size of the piece
	fl.seek(offset);
	int k =0;
	while(k < 10){
		content[k] = fl.readByte();
		k++;
	}
	RandomAccessFile fl2 = new RandomAccessFile("peer_id1/p2", "rw");
	k = 0;
	while(k < 10){
		fl2.writeByte(content[k]);
		k++;
	}
	}
	catch(Exception e){}
 }

}



class MyCallable implements Callable<String> { 
    @Override 
    public String call() throws Exception 
    { 
        System.out.println("Running Code From myCallable");
        return "Return String from MyCallable"; 
    } 
}
