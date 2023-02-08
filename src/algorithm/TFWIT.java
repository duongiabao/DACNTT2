package algorithm;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import tools.MemoryLogger;

public class TFWIT
{
    /** Number of transactions */
    int numOfTrans;
    
    /** object to write the output file **/
    BufferedWriter writer = null;

    /** start time of the last algorithm execution */
    long startTimestamp;

    /** end time of the last algorithm execution */
    long endTimestamp;
    
    /** sum of length of transactions */
    float sumTransLength = 0;
    
    /** List of frequent weighted items */
    List<FWIset> fwis1;
    
    /** The complete set of top-rank-k FWIs */
    List<TRset> fwisTopRankK;
    
    /** Total number of frequent weighted itemsets */
    int countFWIs = 0;      
    
    /** Read the input Trans File */
    ProductDb readTransFile(String filename) throws IOException
    {
        ProductDb pDb = new ProductDb();

        BufferedReader reader = new BufferedReader(new FileReader(filename));
        String line;

        int i = 0;
        while (((line = reader.readLine()) != null))
        {
            Product p = new Product();
            p.transID = ++i;

            String[] lineSplited = line.split(" ");

            for (String itemString : lineSplited)
            {
                Item item = new Item();
                item.name = Integer.parseInt(itemString);
                p.items.add(item); 
            }
            sumTransLength += p.items.size();
            pDb.products.add(p);
        }
        
        reader.close();

        return pDb;
    }
    
    /** Read the input Weights File */
    Map<Integer, Float> readWeightsFile(String filename) throws IOException
    {
        Map<Integer, Float> mapWeights = new HashMap<>();
        BufferedReader reader = new BufferedReader(new FileReader(filename));
        String line;

        int item = 0;
        while (((line = reader.readLine()) != null))
        {
            mapWeights.put(++item, Float.parseFloat(line));
        }
        reader.close();
        return mapWeights;
    }
    
    /**
     * Run the algorithm
     *
     * @param fileNameOfTrans   the input file path of transactions
     * @param fileNameOfWeights   the input file path of weights
     * @param output     the output file path
     * @param rank     top-rank-k
     * @throws IOException if error while reading/writting to file
     */
    public void executeAlogrithm(String fileNameOfTrans, String fileNameOfWeights, String output, int rank) throws IOException
    {
        writer = new BufferedWriter(new FileWriter(output));

        fwis1 = new ArrayList<>();
        /** list of serial numbers of each products*/
        Map<Integer, Float> hashTwOfTrans = new HashMap<>();
        fwisTopRankK = new ArrayList<>();

        ProductDb pDB = readTransFile(fileNameOfTrans);
        Map<Integer, Float> mapWeights = readWeightsFile(fileNameOfWeights);
        numOfTrans = pDB.products.size();
        
        // map of weighted support
        Map<Integer, Float> mapWS = new HashMap<>();

        // sum of all the transaction weight values in a weighted database
        float ttw = 0;
        for (int i = 0; i < pDB.products.size(); i++)
        {
            float sumTransWeight = 0;
            Product pi = pDB.products.get(i);
            for (int j = pi.items.size() - 1; j >= 0; j--)
            {
                Integer item = pi.items.get(j).name;
                mapWS.put(item, (float) 0);
                Float weight = mapWeights.get(item);

                if (weight != null)
                {
                    sumTransWeight += weight;
                }
                else
                {
                    System.out.println("Error: Missing item weight");
                }
            }
            pi.tw = (sumTransWeight / pi.items.size());
            ttw += pi.tw;
            hashTwOfTrans.put(pi.transID, pi.tw);
        }
        
        for (Map.Entry<Integer, Float> entry : mapWS.entrySet())
        {
            Integer item = entry.getKey();
            float ws = 0;
            List<Integer> diffset = new ArrayList<>();
            for (int i = 0; i < pDB.products.size(); i++)
            {
                Product pi = pDB.products.get(i);
                for (int j = pi.items.size() - 1; j >= 0; j--)
                {
                    Integer itemInTrans = pi.items.get(j).name;
                    if (item.equals(itemInTrans))
                    {
                        diffset.add(pi.transID);
                        float tw = pi.tw;
                        ws += tw;
                    }
                }
            }
            mapWS.put(item, (ws / ttw));
            
            FWIset f = new FWIset();
            f.items.add(entry.getKey());
            f.ws = entry.getValue();
            f.diffset = diffset;
            fwis1.add(f);
        }

        Collections.sort(fwis1, FWIset.descendingFrequentComparator);
  
        pDB = null;
        
        MemoryLogger.getInstance().reset();
        startTimestamp = System.currentTimeMillis();
        findFWIs(fwis1, hashTwOfTrans, rank, ttw);
        
        MemoryLogger.getInstance().checkMemory();

        endTimestamp = System.currentTimeMillis();
        
        writeOutputFile(fwisTopRankK);
    }
    
    /**
     * Write the output file
     */
    private void writeOutputFile(List<TRset> fwisTopRankK) throws IOException
    {
        String label = String.format("|%-10s|%-90s|%2s%n", "Rank", "Itemset", "Ws");
        writer.write(label);
        String line = new String(new char[115]).replace('\0', '-');
        writer.write(line);
        writer.newLine();
        for (int i = 0; i < fwisTopRankK.size(); i++)
        {
            StringBuilder item = new StringBuilder();
            for (FWIset fwi : fwisTopRankK.get(i).fwiList)
            {
                item.append(fwi.items + " ");
                countFWIs++;
            }
            String values = String.format("|%-10s|%-90s|%2s%n", i + 1, item, fwisTopRankK.get(i).ws);
            writer.write(values);
        }
        writer.close();
    }
    
    /**
     * Find Frequent weighted itemsets
     */
    private void findFWIs(List<FWIset> is, Map<Integer, Float> hashTwOfTrans, int rank, float ttw) throws IOException
    {
        List<FWIset> candidateK = new ArrayList<>();
        for (int i = 0; i < is.size(); i++)
        {
            if (!fwisTopRankK.isEmpty() && fwisTopRankK.get(fwisTopRankK.size() - 1).ws == is.get(i).ws)
            {
                fwisTopRankK.get(fwisTopRankK.size() - 1).fwiList.add(is.get(i));
                candidateK.add(is.get(i));
            }
            else
            {
                if (fwisTopRankK.size() == rank)
                {
                    break;
                }
                TRset r = new TRset();
                r.ws = is.get(i).ws;
                r.fwiList.add(is.get(i));
                fwisTopRankK.add(r);
                candidateK.add(is.get(i));
            }
        }

        while (!candidateK.isEmpty())
        {
            List<FWIset> candidate = tfwitCandidateGeneration(candidateK, hashTwOfTrans, ttw);

            Collections.sort(candidate, FWIset.descendingFrequentComparator);

            candidateK = new ArrayList<>();

            int i = 0;
            int j = 0;

            while (j < candidate.size() && i < fwisTopRankK.size())
            {
                if (candidate.get(j).ws == fwisTopRankK.get(i).ws)
                {
                    fwisTopRankK.get(i).fwiList.add(candidate.get(j));
                    candidateK.add(candidate.get(j));
                    j++;
                }
                else if (candidate.get(j).ws > fwisTopRankK.get(i).ws)
                {
                    TRset r = new TRset();
                    r.ws = candidate.get(j).ws;
                    r.fwiList.add(candidate.get(j));
                    fwisTopRankK.add(i, r);
                    if (fwisTopRankK.size() > rank)
                    {
                        fwisTopRankK.remove(fwisTopRankK.size() - 1);
                    }
                    candidateK.add(candidate.get(j));
                    j++;
                }
                else
                    i++;
            }

            if (fwisTopRankK.size() < rank && !candidate.isEmpty())
            {
                int z = Math.min((rank - fwisTopRankK.size()), (candidate.size() - j + 1));
                for (i = j; i < (j + z); i++)
                {
                    TRset r = new TRset();
                    r.ws = candidate.get(i).ws;
                    r.fwiList.add(candidate.get(i));
                    fwisTopRankK.add(r);
                }
            }
        }
    }
    
    /**
     * Print statistics about the latest execution of the algorithm to System.out.
     */
    public void printStats()
    {
        System.out.println("========== TFWIT - STATUS ============");
        System.out.println(" Number of transactions: " + numOfTrans);
        System.out.println(" Number of frequent 1-items  : " + fwis1.size());
        System.out.println(" sumTransLength : " + sumTransLength);
        System.out.println(" Avg. Trans. size : " + (sumTransLength/numOfTrans));
        System.out.println(" Number of frequent weight itemsets: " + countFWIs);
        System.out.println(" Total time ~: " + (endTimestamp - startTimestamp) + " ms");
        System.out.println(" Max memory:" + MemoryLogger.getInstance().getMaxMemory() + " MB");
        System.out.println("==========================================");
    }
    
    /**
     * TFWIT algorithm
     * 
     * @param candidateK a list
     * @param hashTwOfTrans a map
     * @param ttw float
     * @return 
     */
    private List<FWIset> tfwitCandidateGeneration(List<FWIset> candidateK, Map<Integer, Float> hashTwOfTrans, float ttw)
    {
        List<FWIset> candidateNext = new ArrayList<>();
        for (int i = candidateK.size() - 1; i > 0; i--)
        {
            FWIset cI = candidateK.get(i);
            for (int j = i - 1; j >= 0; j--)
            {
                FWIset cJ = candidateK.get(j);
                FWIset c = new FWIset();
                if (checkSameEquivalence(cI, cJ))
                {
                    FloatByRef sumTw = new FloatByRef(0);
                    c.diffset = tidsetCombination(cI.diffset, cJ.diffset, hashTwOfTrans, sumTw);
                    c.ws = (sumTw.value / ttw);
                    c.items = itemUnion(cI.items, cJ.items);
                    candidateNext.add(c);
                }
            }
        }

        return candidateNext;
    }
    
    /**
     * Perform the union of two list of items
     * 
     * @param a a list
     * @param b another list
     * @return the union
     */
    List<Integer> itemUnion(List<Integer> a, List<Integer> b)
    {
        List<Integer> result = new ArrayList<>();
        int i = 0;
        while (i < a.size())
        {
            result.add(a.get(i));
            i++;
        }
        result.add(b.get(b.size() - 1));
        return result;
    }
    
    /**
     * Perform combinations
     * 
     * @param a
     * @param b
     * @return
     */
    private List<Integer> tidsetCombination(List<Integer> a, List<Integer> b,Map<Integer, Float> hashTwOfTrans, FloatByRef sumTw)
    {
        List<Integer> result = new ArrayList<>();
        for (int j = 0; j < b.size(); j++)
        {
            Integer bJ = b.get(j);
            for (int i = 0; i < a.size(); i++)
            {
                Integer aI = a.get(i);
                if(Objects.equals(bJ, aI))
                {
                    result.add(bJ);
                    sumTw.value += hashTwOfTrans.get(bJ);
                    break;
                }               
            }
        }
        return result;
    }
    
    /**
     * Class FloatByRef to pass an float by reference
     */
    class FloatByRef
    {
        float value;

        FloatByRef(float value)
        {
            this.value = value;
        }
    }
    
    /** check same equivalence */
    private boolean checkSameEquivalence(FWIset cI, FWIset cJ)
    {
        if (cI.items.size() == 1 && cJ.items.size() == 1)
        {
            return true;
        }
        else
        {
            int i = 0;
            int j = 0;
            boolean flag = true;
            while (i < cI.items.size() - 1 && j < cJ.items.size() - 1)
            {
                if (!Objects.equals(cI.items.get(i), cJ.items.get(j)))
                {
                    flag = false;
                    break;
                }
                i++;
                j++;
            }

            return flag;
        }
    }        
}

/** Class Top Rank */
class TRset
{
    List<FWIset> fwiList;
    float ws;

    public TRset()
    {
        fwiList = new ArrayList<>();
    }
}

/** Class representing a frequent weighted itemset */
class FWIset
{
    List<Integer> items;
    float ws;
    List<Integer> diffset;

    public FWIset()
    {
        items = new ArrayList<>();
        diffset = new ArrayList<>();
    }

    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        for (Integer item : items)
        {
            sb.append(item);
            sb.append(' ');
        }
        sb.append("#WS: ");
        sb.append(this.ws);
        return sb.toString();
    }

    static Comparator<FWIset> descendingFrequentComparator = new Comparator<FWIset>()
    {
        @Override
        public int compare(FWIset x, FWIset y)
        {
            if (x.ws > y.ws)
                return -1;
            else if (x.ws < y.ws)
                return 1;
            else
                return x.items.get(0).compareTo(y.items.get(0));

        }
    };
}
