package SearchEngineTools;

import SearchEngineTools.ParsingTools.Term.ATerm;
import javafx.util.Pair;

import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

public class ConcurrentBuffer {
    private Semaphore semaphore;
    private ConcurrentLinkedQueue<Pair<Iterator<ATerm>,Integer>> concurrentLinkedQueue;

    public ConcurrentBuffer() {
        semaphore=new Semaphore(0);
        concurrentLinkedQueue=new ConcurrentLinkedQueue<>();
    }

    public void add(Pair<Iterator<ATerm>,Integer> terms){
        concurrentLinkedQueue.add(terms);
        semaphore.release();
    }

    public Pair<Iterator<ATerm>,Integer> get(){
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return concurrentLinkedQueue.poll();
    }
}
