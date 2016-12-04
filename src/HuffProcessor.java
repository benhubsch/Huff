import java.util.*;

/**
 *	Interface that all compression suites must implement. That is they must be
 *	able to compress a file and also reverse/decompress that process.
 * 
 *	@author Brian Lavallee
 *	@since 5 November 2015
 *  @author Owen Atrachan
 *  @since December 1, 2016
 */
public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); // or 256
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;
	public static final int HUFF_COUNTS = HUFF_NUMBER | 2;

	public enum Header{TREE_HEADER, COUNT_HEADER};
	public Header myHeader = Header.TREE_HEADER;
	
	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){
	    int[] counts = readForCounts(in);
	    TreeNode root = makeTreeFromCounts(counts);
	    String[] codings = makeCodingsFromTree(root, "", new String[257]);
	    
	    out.writeBits(BITS_PER_INT, HUFF_TREE);
	    writeHeader(root, out);
	    
	    in.reset();
	    
	    writeCompressedBits(in, codings, out);
	}
	
	public int[] readForCounts(BitInputStream in){
		int[] counts = new int[256];
		while (true){
			int countBit = in.readBits(BITS_PER_WORD);
			if (countBit == -1) break;
			
			counts[countBit] +=1;
		}
		return counts;
	}
	
	public TreeNode makeTreeFromCounts(int[] counts){
		PriorityQueue<TreeNode> pq = new PriorityQueue<>();
		
		for(int i=0; i < 256; i++){
			if (counts[i] > 0){
				pq.add(new TreeNode(i, counts[i]));
			}
		}
		pq.add(new TreeNode(PSEUDO_EOF, 1));
		
		while (pq.size() > 1){
			TreeNode left = pq.remove();
			TreeNode right = pq.remove();
			TreeNode t = new TreeNode(-1, left.myWeight + right.myWeight, left, right);
			pq.add(t);
		}
		
		TreeNode root = pq.remove();
		return root;
	}

	public String[] makeCodingsFromTree(TreeNode root, String path, String[] codings){
		if (root.myLeft == null && root.myRight == null){
			codings[root.myValue] = path;
			return codings;
		}

		makeCodingsFromTree(root.myLeft, (new StringBuilder(path).append("0")).toString(), codings);
		makeCodingsFromTree(root.myRight, (new StringBuilder(path).append("1")).toString(), codings);
		
		return codings;
	}

	public void writeHeader(TreeNode root, BitOutputStream out){
		if (root.myValue == -1){ //if we hit an internal node
			out.writeBits(1, 0);
		}
		if (root.myValue >= 0){ //if we hit a leaf
			out.writeBits(1, 1);
			out.writeBits(9, root.myValue);
			return;
		}
		
		writeHeader(root.myLeft, out);
		writeHeader(root.myRight, out);
	}
	
	public void writeCompressedBits(BitInputStream in, String[] codings, BitOutputStream out){
		while (true){
			int eightBit = in.readBits(BITS_PER_WORD);
			if (eightBit == -1) break;
			
			String encode = codings[eightBit];
			out.writeBits(encode.length(), Integer.parseInt(encode, 2));
		}
		out.writeBits(codings[256].length(), Integer.parseInt(codings[256], 2));
	}
	
	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){
		int id = in.readBits(BITS_PER_INT);
//		System.out.println(id);
		if (id != HUFF_TREE) throw new HuffException("error");
		
		TreeNode root = readTreeHeader(in);
		readCompressedBits(root, in, out);
	}
	
	public TreeNode readTreeHeader(BitInputStream in){
		int treeBit = in.readBits(1);
		if (treeBit == 1){
			return new TreeNode(in.readBits(9), 1);
		}
		TreeNode internal = new TreeNode(7, 9, null, null);
		internal.myLeft = readTreeHeader(in);
		internal.myRight = readTreeHeader(in);
		return internal;
	}
	
	public void readCompressedBits(TreeNode root, BitInputStream in, BitOutputStream out){
		TreeNode current = root;
		
		while (true){
			int treeBit = in.readBits(1);
			if (treeBit == -1) throw new HuffException("bad input, no PSEUDO_EOF");
			
			if (treeBit == 0) current = current.myLeft;
			else if (treeBit == 1) current = current.myRight;
			
			if (current.myLeft == null && current.myRight == null){
				if (current.myValue == PSEUDO_EOF) break;
				else{
					out.writeBits(BITS_PER_WORD, current.myValue);
					current = root;
				}
			}
		}
	}
	
	
	public void setHeader(Header header) {
        myHeader = header;
        System.out.println("header set to "+myHeader);
    }
	
}





//if (root.myValue == PSEUDO_EOF) break;
//if (root.myLeft == null && root.myRight == null){
//	out.write(root.myValue);
//	root = originalRoot;
//}
//int treeBit = in.readBits(1);
//if (treeBit == 0) root = root.myLeft;
//else if (treeBit == 1) root = root.myRight;