package algorithm;

import tools.MemoryLogger;
import java.io.*;
import java.util.*;

public class TFWINplus
{
    int pre;
    int post;

    /** Number of transactions */
    int numOfTrans;

    /** object to write the output file **/
    BufferedWriter writer = null;

    /** start time of the last algorithm execution */
    long startTimestamp;

    /** end time of the last algorithm execution */
    long endTimestamp;

    /** Total number of frequent weighted itemsets */
    int countFWIs = 0;

    /** List of frequent weighted items */
    List<FWI> fwis1;

    /** The complete set of top-rank-k FWIs */
    List<TR> fwisTopRankK;

    /** list of serial numbers of each products */
    Map<Integer, Integer> hashI1;
    
    /** sum of length of transactions */
    float sumTransLength = 0;

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
     * @throws IOException
     */
    public void executeAlogrithm(String fileNameOfTrans, String fileNameOfWeights, String output, int rank) throws IOException
    {
        writer = new BufferedWriter(new FileWriter(output));

        pre = 0;
        post = 0;

        fwis1 = new ArrayList<>();
        hashI1 = new HashMap<>();
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
        }
        
        for (Map.Entry<Integer, Float> entry : mapWS.entrySet())
        {
            Integer item = entry.getKey();
            float ws = 0;
            for (int i = 0; i < pDB.products.size(); i++)
            {
                Product pi = pDB.products.get(i);
                for (int j = pi.items.size() - 1; j >= 0; j--)
                {
                    Integer itemInTrans = pi.items.get(j).name;
                    if (item.equals(itemInTrans))
                    {
                        float tw = pi.tw;
                        ws += tw;
                    }
                }
            }
            mapWS.put(item, (ws / ttw));
            
            FWI f = new FWI();
            f.items.add(entry.getKey());
            f.ws = entry.getValue();
            fwis1.add(f);
        }
        
        Collections.sort(fwis1, FWI.descendingFrequentComparator);

        for (int i = 0; i < fwis1.size(); i++)
            hashI1.put(fwis1.get(i).items.get(0), i);

        WnNode root = new WnNode();
        root.item.name = -1;
        for (int i = 0; i < pDB.products.size(); i++)
        {
            Product pDBi = pDB.products.get(i);
            for (int l = pDBi.items.size() - 1; l >= 0; l--)
            {
                Item itemL = pDBi.items.get(l);
                if (hashI1.get(itemL.name) == null)
                    pDBi.items.remove(l);
                else
                    itemL.ws = fwis1.get(hashI1.get(itemL.name)).ws;
            }
            pDBi.Sort();
            insertTree(pDBi, root);
        }
        pDB = null;

        generateOrder(root);

        generateNCSets(root);

        MemoryLogger.getInstance().reset();
        startTimestamp = System.currentTimeMillis();
        findFWIs(fwis1, rank, ttw);

        MemoryLogger.getInstance().checkMemory();

        endTimestamp = System.currentTimeMillis();
        
        writeOutputFile(fwisTopRankK);
    }

    /**
     * Print statistics about the latest execution of the algorithm to System.out.
     */
    public void printStats()
    {
        System.out.println("========== TFWINplus - STATUS ============");
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
     * Write the output file
     */
    private void writeOutputFile(List<TR> fwisTopRankK) throws IOException
    {
        String label = String.format("|%-10s|%-90s|%2s%n", "Rank", "Itemset", "Ws");
        writer.write(label);
        String line = new String(new char[115]).replace('\0', '-');
        writer.write(line);
        writer.newLine();
        for (int i = 0; i < fwisTopRankK.size(); i++)
        {
            StringBuilder item = new StringBuilder();
            for (FWI fwi : fwisTopRankK.get(i).fwiList)
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
    private void findFWIs(List<FWI> is, int rank, float ttw) throws IOException
    {
        List<FWI> candidateK = new ArrayList<>();
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
                TR r = new TR();
                r.ws = is.get(i).ws;
                r.fwiList.add(is.get(i));
                fwisTopRankK.add(r);
                candidateK.add(is.get(i));
            }
        }

        float threshold = fwisTopRankK.get(fwisTopRankK.size() - 1).ws;
        while (!candidateK.isEmpty())
        {
            List<FWI> candidate = tfwinPlusCandidateGeneration(candidateK, threshold, ttw);
            
            Collections.sort(candidate, FWI.descendingFrequentComparator);

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
                    TR r = new TR();
                    r.ws = candidate.get(j).ws;
                    r.fwiList.add(candidate.get(j));
                    fwisTopRankK.add(i, r);
                    if (fwisTopRankK.size() > rank)
                    {
                        fwisTopRankK.remove(fwisTopRankK.size() - 1);
                        threshold = fwisTopRankK.get(fwisTopRankK.size() - 1).ws;
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
                    TR r = new TR();
                    r.ws = candidate.get(i).ws;
                    r.fwiList.add(candidate.get(i));
                    fwisTopRankK.add(r);
                }
            }
        }
    }

    /**
     * TFWINPlus algorithm
     * 
     * @param candidateK a list
     * @param threshold float
     * @param ttw float
     * @return 
     */
    private List<FWI> tfwinPlusCandidateGeneration(List<FWI> candidateK, float threshold, float ttw)
    {
        List<FWI> candidateNext = new ArrayList<>();
        for (int i = candidateK.size() - 1; i > 0; i--)
        {
            FWI cI = candidateK.get(i);
            for (int j = i - 1; j >= 0; j--)
            {
                FWI cJ = candidateK.get(j);
                FWI c = new FWI();
                if (checkSameEquivalence(cI, cJ))
                {
                    if (cI.ws < threshold || cJ.ws < threshold)
                        continue;
                    FloatByRef sumTw = new FloatByRef(0);
                    c.nCs = nodeCodeCombination(cI.nCs, cJ.nCs, sumTw);
                    c.ws = (sumTw.value / ttw);
                    if (c.ws < threshold)
                        continue;
                    c.items = itemUnion(cI.items, cJ.items);
                    candidateNext.add(c);
                }
            }
        }
        
        return candidateNext;
    }

    /**
     * Perform combinations
     * 
     * @param a
     * @param b
     * @return
     */
    private List<NodeCode> nodeCodeCombination(List<NodeCode> a, List<NodeCode> b, FloatByRef sumTw)
    {
        List<NodeCode> result = new ArrayList<>();

        for (int j = 0; j < b.size(); j++)
        {
            NodeCode bJ = b.get(j);
            for (int i = 0; i < a.size(); i++)
            {
                NodeCode aI = a.get(i);
                if (bJ.preOrder < aI.preOrder && bJ.postOrder > aI.postOrder)
                {
                    if (!result.isEmpty() && result.get(result.size() - 1).preOrder == bJ.preOrder && result.get(result.size() - 1).postOrder == bJ.postOrder)
                    {
                        result.get(result.size() - 1).tw += aI.tw;
                    }
                    else
                    {
                        NodeCode temp = new NodeCode();
                        temp.preOrder = bJ.preOrder;
                        temp.postOrder = bJ.postOrder;
                        temp.tw = aI.tw;
                        result.add(temp);
                    }
                    sumTw.value += aI.tw;
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

    /** check same equivalence */
    private boolean checkSameEquivalence(FWI cI, FWI cJ)
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

    /**
     * Generate NC sets
     * 
     * @param root the root of a tree
     */
    private void generateNCSets(WnNode root)
    {
        if (root.item.name != -1)
        {
            int stt = hashI1.get(root.item.name);
            NodeCode nc = new NodeCode();
            nc.preOrder = root.preOrder;
            nc.postOrder = root.postOrder;
            nc.tw = root.tw;
            fwis1.get(stt).nCs.add(nc);
        }

        for (WnNode node : root.childNodes)
            generateNCSets(node);
    }

    /**
     * Generate order
     * 
     * @param root the root of a tree
     */
    private void generateOrder(WnNode root)
    {

        root.preOrder = pre++;
        for (int i = 0; i < root.childNodes.size(); i++)
        {
            generateOrder(root.childNodes.get(i));
        }
        root.postOrder = post++;
    }

    /**
     * Insert a product in the tree
     * 
     * @param p    product
     * @param root the tree root
     */
    private void insertTree(Product pro, WnNode root)
    {
        while (!pro.items.isEmpty())
        {
            Item item = pro.items.get(0);
            pro.items.remove(0);

            boolean flag = false;
            WnNode node = new WnNode();

            for (int i = 0; i < root.childNodes.size(); i++)
            {
                if (root.childNodes.get(i).item.name == item.name)
                {
                    root.childNodes.get(i).tw += pro.tw;
                    node = root.childNodes.get(i);
                    flag = true;
                    break;
                }
            }
            if (!flag)
            {
                node.item = item;
                node.tw = pro.tw;
                root.childNodes.add(node);
            }
            insertTree(pro, node);
        }
    }
}

/** Class Node-Code (PP-code) */
class NodeCode
{
    int postOrder;
    int preOrder;
    float tw;
}

/** Class Top Rank */
class TR
{
    List<FWI> fwiList;
    float ws;

    public TR()
    {
        fwiList = new ArrayList<>();
    }
}

/** Class representing a frequent weighted itemset */
class FWI
{
    List<Integer> items;
    float ws;
    List<NodeCode> nCs;

    public FWI()
    {
        items = new ArrayList<>();
        nCs = new ArrayList<>();
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

    static Comparator<FWI> descendingFrequentComparator = new Comparator<FWI>()
    {
        @Override
        public int compare(FWI x, FWI y)
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

/** Class representing a product database */
class ProductDb
{
    List<Product> products;

    ProductDb()
    {
        products = new ArrayList<>();
    }
}

/** Class representing a product */
class Product
{
    int transID;
    List<Item> items;
    float tw;

    void Sort()
    {
        Collections.sort(items, Item.itemComparator);
    }

    Product()
    {
        transID = 0;
        items = new ArrayList<>();
        tw = 0;
    }

}

/** Class representing an item */
class Item
{
    int name;
    float ws;

    static Comparator<Item> itemComparator = new Comparator<Item>()
    {
        public int compare(Item x, Item y)
        {
            if (x.ws > y.ws)
                return -1;
            else if (x.ws < y.ws)
                return 1;
            else
                return x.name - y.name;

        }
    };
}

/** Class representing a WPPC node */
class WnNode
{
    Item item;
    List<WnNode> childNodes;
    int preOrder;
    int postOrder;
    float tw;

    public WnNode()
    {
        item = new Item();
        childNodes = new ArrayList<>();
        preOrder = 0;
        postOrder = 0;
        tw = 0;
    }
}
