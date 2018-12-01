package SearchEngineTools;

import javafx.util.Pair;

import java.util.Comparator;

public class PostingListComparator implements Comparator<Pair<String, Integer>> {
    @Override
    public int compare(Pair<String, Integer> o1, Pair<String, Integer> o2) {
        String postingList1=o1.getKey();
        String postingList2=o2.getKey();
        int compareResult=postingList1.substring(0,postingList1.indexOf(";")).compareTo(postingList2.substring(0,postingList2.indexOf(";")));
        if(compareResult==0){
            postingList1=postingList1.substring(postingList1.indexOf(";")+1);
            postingList2=postingList2.substring(postingList2.indexOf(";")+1);
            String[] splitPostingList1=postingList1.split(" ");
            String[] splitPostingList2=postingList2.split(" ");
            int lastDocID1=0;
            int lastDocID2=0;
            for (int i = 0; i < splitPostingList1.length; i+=2)
                lastDocID1+= Integer.parseInt(splitPostingList1[i]);
            for (int i = 0; i < splitPostingList2.length; i+=2)
                lastDocID2+= Integer.parseInt(splitPostingList2[i]);
            return lastDocID1-lastDocID2;
        }
         else
             return compareResult;
    }
}
