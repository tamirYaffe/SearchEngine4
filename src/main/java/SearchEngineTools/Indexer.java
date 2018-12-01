package SearchEngineTools;

import SearchEngineTools.ParsingTools.Term.ATerm;
import SearchEngineTools.ParsingTools.Term.CityTerm;
import SearchEngineTools.ParsingTools.Term.WordTerm;
import javafx.util.Pair;
import sun.awt.Mutex;

import java.io.*;
import java.util.*;

public class Indexer {
    //dictionary that holds term->idf. used also for word check for big first word.
    private Map<String, Pair<Integer,Integer>> dictionary;
    //dictionary and posting list in one hash
    private Map<String, PostingList> tempInvertedIndex;
    //dictionary and posting list in one hash for cities.
    private Map<String, List<CityPostingEntry>> tempCityInvertedIndex;


    private int memoryBlockSize;
    private int usedMemory;
    private int blockNum;


    public Indexer() {
        dictionary = new LinkedHashMap<>();
        tempInvertedIndex = new LinkedHashMap<>();
        tempCityInvertedIndex=new HashMap<>();
    }

    public Indexer(Map<String, Pair<Integer,Integer>> dictionary) {
        this.dictionary = dictionary;
        tempInvertedIndex = new LinkedHashMap<>();
    }

    public Indexer(int memoryBlockSize) {
        this();
        this.memoryBlockSize = memoryBlockSize;
    }

    public Indexer(int memoryBlockSize, int blockNum) {
        this(memoryBlockSize);
        this.blockNum=blockNum;
    }

    /**
     * Creates the dictionary and posting files.
     *
     * @param terms - list of the document terms(after parse).
     * @param docID
     */
    public  void  createInvertedIndex(Iterator<ATerm> terms, int docID) {
        System.out.println("started indexing: "+docID);
        Document document=new Document(docID);
        while (terms.hasNext()) {
            ATerm aTerm = terms.next();
            if (aTerm instanceof WordTerm)
                handleCapitalWord(aTerm);
            String term = aTerm.getTerm();

            //need to fix!!!
            if(term.contains(";"))
                term=term.substring(0,term.indexOf(";"))+"_fixed";
            if(aTerm instanceof CityTerm){
                document.setDocCity(term);
                addToCityIndex(aTerm,docID);
            }
            int termOccurrences=aTerm.getOccurrences();
            document.updateDocInfo(termOccurrences);
            //add or update dictionary.
            if (!dictionary.containsKey(term))
                dictionary.put(term, new Pair<>(1,-1));
            else
                dictionary.replace(term,new Pair<>(dictionary.get(term).getKey()+1,-1));
            PostingList postingsList;
            PostingEntry postingEntry = new PostingEntry(docID, termOccurrences);
            if (!tempInvertedIndex.containsKey(term)) {
                postingsList = new PostingList();
                tempInvertedIndex.put(term, postingsList);
                usedMemory += term.length() + 1;
            } else {
                postingsList = tempInvertedIndex.get(term);
            }
            int addedMemory=postingsList.add(postingEntry);
            if(addedMemory!=-1)
                usedMemory+=addedMemory;
            if (usedMemory > memoryBlockSize) {
                sortAndWriteInvertedIndexToDisk();
                //init dictionary and posting lists.
                tempInvertedIndex.clear();
                usedMemory = 0;
            }
        }
        document.writeDocInfoToDisk();
        //System.out.println("finish: " + docID);
    }

    public void mergeBlocks() throws IOException {
        System.out.println("starting merge");
        int postingListIndex=0;
        BufferedReader[] readers = new BufferedReader[blockNum];
        PostingListComparator comparator=new PostingListComparator();
        PriorityQueue<Pair<String, Integer>> queue = new PriorityQueue<>(comparator);
        FileWriter fw = new FileWriter("postingLists.txt",true);
        BufferedWriter bw = new BufferedWriter(fw);
        String curPostingList;
        List<String> bufferPostingLists=new ArrayList<>();
        //create readers and init queue.
        for (int i = 0; i < blockNum; i++) {
            String fileName = "blocks/block" + i + ".txt";
            readers[i] = new BufferedReader(new FileReader(fileName));
            queue.add(new Pair<>(readers[i].readLine(), i));
        }
        while (!queue.isEmpty()) {
            curPostingList=getNextPostingList(queue,readers);
            curPostingList=checkForMergeingPostingLines(queue,readers,curPostingList);
            String term=extractTerm(curPostingList);
            dictionary.replace(term,new Pair<>(dictionary.get(term).getKey(),postingListIndex++));
            //write to buffer posting lists
            bufferPostingLists.add(curPostingList);
            usedMemory+=curPostingList.length()-term.length();
            //check size of buffer
            if (usedMemory > memoryBlockSize) {
                writeBufferPostingListsToDisk(bw,bufferPostingLists);
                //init dictionary and posting lists.
                bufferPostingLists.clear();
                usedMemory = 0;
            }

        }
        //writing buffer remaining posting lists
        if(usedMemory>0){
            writeBufferPostingListsToDisk(bw,bufferPostingLists);
        }
        bw.close();
        writeDictionaryToDisk();
        //sortAndWriteDictionaryToDisk();
    }

    public  void sortAndWriteInvertedIndexToDisk() {
        if(usedMemory==0)
            return;
        System.out.println("writing to disk: blockNum" +blockNum+" "+ usedMemory+" bytes");
        String fileName = "blocks/block" + blockNum + ".txt";
        blockNum++;
        try (FileWriter fw = new FileWriter(fileName);
             BufferedWriter bw = new BufferedWriter(fw)) {

            List<String> keys = new ArrayList<>(tempInvertedIndex.keySet());
            Collections.sort(keys);
            for (String key : keys) {
                bw.write(key+";");
                bw.write(""+tempInvertedIndex.get(key));
                bw.newLine();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("finished writing blockNum"+blockNum);
    }

    public void loadDictionaryFromDisk(String path){
        try {
            BufferedReader reader = new BufferedReader(new FileReader(path));
            String line;
            while (( line=reader.readLine())!=null){
                dictionary.put(line.split(" ")[0],new Pair<>(Integer.valueOf(line.split(" ")[1]),Integer.valueOf(line.split(" ")[2])) );
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void addToCityIndex(ATerm aTerm, int docID) {
        CityTerm cityTerm= (CityTerm) aTerm;
        List<CityPostingEntry> postingsList;
        CityPostingEntry postingEntry=new CityPostingEntry(docID,cityTerm.getPositions());
        String term=aTerm.getTerm();
        if (!tempCityInvertedIndex.containsKey(term)) {
            postingsList = new ArrayList<>();
            tempCityInvertedIndex.put(term, postingsList);
        } else {
            postingsList = tempCityInvertedIndex.get(term);
        }
        postingsList.add(postingEntry);
    }

    //<editor-fold desc="Private functions">
    private String extractTerm(String postingList) {
        return postingList.substring(0,postingList.indexOf(";"));
    }

    private void writeBufferPostingListsToDisk(BufferedWriter bw, List<String> bufferPostingLists) throws IOException {
        for(String postingList:bufferPostingLists){
            bw.write(postingList.substring(postingList.indexOf(";")+1));
//            bw.write(postingList);
            bw.newLine();
        }
    }

    private void writeDictionaryToDisk() {
        try (FileWriter fw = new FileWriter("dictionary.txt");
             BufferedWriter bw = new BufferedWriter(fw)) {
            for(Map.Entry entry: dictionary.entrySet()){
                bw.write(entry.getKey()+" "+((Pair<Integer,Integer>)entry.getValue()).getKey()+" "+((Pair<Integer,Integer>)entry.getValue()).getValue());
                bw.newLine();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * remove top of queue,and add the next line from the removed line block.
     * @param queue
     * @param readers
     * @return
     * @throws IOException
     */
    private String getNextPostingList(PriorityQueue<Pair<String, Integer>> queue, BufferedReader[] readers) throws IOException {
        String postingList;
        while (true) {
            Pair<String,Integer> postingListPair=queue.poll();
            postingList = postingListPair.getKey();
            int blockIndex=postingListPair.getValue();

            String nextPostingList=readers[blockIndex].readLine();
            if(nextPostingList!=null)
                queue.add(new Pair<>(nextPostingList,blockIndex));
            //handling words lower/upper case
            String term=extractTerm(postingList);
            if(Character.isUpperCase(term.charAt(0)) && dictionary.containsKey(term.toLowerCase())){
                //change posting list
                String updatedPostingList=term.toLowerCase()+postingList.substring(postingList.indexOf(";"));
                // add to queue
                queue.add(new Pair<>(updatedPostingList,blockIndex));
            }
            else
                break;

        }
        return postingList;
    }

    private String checkForMergeingPostingLines(PriorityQueue<Pair<String, Integer>> queue, BufferedReader[] readers, String curPostingList) throws IOException {
        if(queue.isEmpty())
            return curPostingList;
        String nextPostingList=queue.peek().getKey();
        while (extractTerm(curPostingList).equals(extractTerm(nextPostingList))){
            curPostingList=mergeAndSortPostingLists(curPostingList,nextPostingList);
            getNextPostingList(queue,readers);
            if(queue.isEmpty())
                break;
            nextPostingList=queue.peek().getKey();
        }
        return curPostingList;
    }

    private String mergePostingLists(String postingList1, String postingList2) {
        String term=extractTerm(postingList1);
        postingList1=postingList1.substring(postingList1.indexOf(";")+1);
        postingList2=postingList2.substring(postingList2.indexOf(";")+1);
        int lastDocID1=PostingList.calculateLastDocID(postingList1);
        String firstDocID=postingList2.substring(0,postingList2.indexOf(" "));
        int firstDocID2=Integer.parseInt(firstDocID)-lastDocID1;
        firstDocID=""+firstDocID2;
        postingList2=firstDocID+postingList2.substring(postingList2.indexOf(" "));
        return term+";"+postingList1+" "+postingList2;
    }

    private String mergeAndSortPostingLists(String postingList1, String postingList2) {
        String term=extractTerm(postingList1);
        PostingList postingList_1=new PostingList(postingList1);
        PostingList postingList_2=new PostingList(postingList2);
        return term+";"+PostingList.mergeLists(postingList_1,postingList_2);
    }

    private void handleCapitalWord(ATerm aTerm) {
        String term = aTerm.getTerm();
        String termLowerCase = term.toLowerCase();
        String termUpperCase = term.toUpperCase();
        if (term.equals(""))
            return;
        //term is upper case.
        if (Character.isUpperCase(term.charAt(0))) {
            if (dictionary.containsKey(termLowerCase)) {
                ((WordTerm) aTerm).toLowerCase();
            }
        }
        //term is lower case.
        else {
            if (dictionary.containsKey(termUpperCase)) {
                //change termUpperCase in dictionary to termLowerCase
                Pair<Integer,Integer> dictionaryPair = dictionary.remove(termUpperCase);
                dictionary.put(termLowerCase, dictionaryPair);
            }
        }
    }

    private void sortAndWriteDictionaryToDisk() {
        try (FileWriter fw = new FileWriter("dictionary.txt");
             BufferedWriter bw = new BufferedWriter(fw)) {

            List<String> keys = new ArrayList<>(dictionary.keySet());
            Collections.sort(keys);
            for (String key : keys) {
                Pair<Integer,Integer>dictionaryPair=dictionary.get(key);
                bw.write(key+" "+dictionaryPair.getKey()+" "+dictionaryPair.getValue());
                bw.newLine();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    //</editor-fold>
}
