import org.apache.commons.math3.special.Erf;

import java.io.*;
import java.util.*;

public class Ratings {
	static final int TOTAL_CONTESTS = 12;
	static final double[] WEEKLY_BONUS = new double[] {0,0,0,0,0,0,0,0,0,0,0,0};
	static final double K_CONSTANT = 0.35;
	static final double LOGISTIC_CONSTANT = (2 * (1 + Math.exp(-1 * K_CONSTANT * TOTAL_CONTESTS)))/(2 - (1 + Math.exp(-1 * K_CONSTANT * TOTAL_CONTESTS)));


	public static void main(String[] args) throws Exception {
		// Initialize Readers and Writers
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		BufferedReader fr = new BufferedReader(new FileReader("ratings.txt"));
		BufferedWriter wr = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("newRatings.txt"), "UTF-8"));
		BufferedWriter gp = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("newGrandPrix.txt"), "UTF-8"));
		String line;
		int numCoders;
		double averageRating;
		int curContest;
		double CF;
		
		// Initialize the database of coders
		Hashtable<String, Coder> database = new Hashtable<String, Coder> ();
				
		// Retrieve data from the ratings file
		curContest = Integer.parseInt(fr.readLine()) + 1;
		line = fr.readLine();
		while(line != null && line.length() != 0) {
			String[] lineData = line.split(" ");
			String id = lineData[0];
			double rating = Double.parseDouble(lineData[1]);
			double volatility = Double.parseDouble(lineData[2]);
			int num = Integer.parseInt(lineData[3]);
			int recent = Integer.parseInt(lineData[4]);
			double oldpoints = Double.parseDouble(lineData[5]);
			
			Coder curCoder = new Coder(id, rating, volatility, num, recent, oldpoints, oldpoints);
			database.put(id, curCoder);
			line = fr.readLine();
		}
		
		// Read the rankings from the most recent contest
		ArrayList<String> contestRankings = new ArrayList<String> ();
		while((line = br.readLine()).length() != 0) {
			contestRankings.add(line);
		}
		numCoders = contestRankings.size();
		
		// Add new contestants
		for(int i=0; i<numCoders; i++) {
			if(!database.containsKey(contestRankings.get(i))) {
				Coder newCoder = new Coder(contestRankings.get(i), 1500, 400, 0, curContest, 0, 0);
				database.put(newCoder.id, newCoder);
			}
		}
		
		// Gather coder data
		Coder[] clist = new Coder[numCoders];
		Coder[] newclist = new Coder[numCoders];
		for(int i=0; i<numCoders; i++) {
			clist[i] = database.get(contestRankings.get(i));
		}
		
		// Compute averageRating
		double total = 0;
		for(int i=0; i<numCoders; i++) {
			total += clist[i].rating;
		}
		averageRating = total / ((double)clist.length);
		
		// Compute Competition Factor
		double totalVolSq = 0, totalRatDiffSq = 0;
		for(int i=0; i<numCoders; i++) {
			totalVolSq += Math.pow(clist[i].volatility, 2);
			totalRatDiffSq += Math.pow(clist[i].rating - averageRating, 2);
		}
		CF = Math.pow(totalVolSq / ((double) numCoders) + totalRatDiffSq / ((double)(numCoders - 1)), 0.5);
		
		// Adjust all Ratings
		for (int i=0; i<numCoders; i++) {
			double erank = ERank(clist, i);
			double eperf = Perf(erank, numCoders);
			double aperf = Perf(i+1, numCoders);
			double perfas = PerfAs(clist[i].rating, CF, aperf, eperf);
			double weight = Weight(clist[i].num);
			double cap = Cap(clist[i].num);
			double newrating = NewRating(clist[i].rating, weight, perfas, cap);
			double newvolatility = NewVolatility(newrating, clist[i].rating, weight, clist[i].volatility);
			double gppoints = GPCalc(newrating, clist[i].num + 1, curContest);
			Coder newCoder = new Coder(clist[i].id, newrating, newvolatility, clist[i].num + 1, curContest, clist[i].points, gppoints);
			newclist[i] = newCoder;
		}
		
		// Update database
		for(int i=0; i<numCoders; i++) {
			database.put(newclist[i].id, newclist[i]);
		}
		
		// Aggregate all coders
		Coder[] allCoders = new Coder[database.size()];
		
		Set<String> keys = database.keySet();
		int index = 0;
		for(String id : keys) {
			allCoders[index] = database.get(id);
			index++;
		}
		
		// Sort
		Arrays.sort(allCoders, new Comparator<Coder>() { 
			public int compare(Coder o1, Coder o2) {
				return (int)o2.rating - (int)o1.rating;
			}
		});
		
		// Print all coders
		wr.write(curContest + "\n");
		for(int i=0; i<allCoders.length; i++) {
			wr.write(allCoders[i].id + " " + allCoders[i].rating + " " + allCoders[i].volatility + " " + allCoders[i].num + " " + allCoders[i].recent + " " + allCoders[i].points);
			wr.write("\n");
		}
		
		wr.close();
		
		// Sort Scores
		Arrays.sort(allCoders, new Comparator<Coder>() {
			public int compare(Coder o1, Coder o2) {
				return (int)o2.points - (int)o1.points;
			}
		});
		
		// Print all scores
		gp.write(curContest + "\n");
		for(int i=0; i<allCoders.length; i++) {
			int diff = (int)allCoders[i].points - (int)allCoders[i].oldPoints;
			int num = allCoders[i].num;
			if(num > 1) {
				gp.write(allCoders[i].id + " " + (int)allCoders[i].points + " +" + diff);
			}
			else {
				gp.write(allCoders[i].id + " " + (int)allCoders[i].points);
			}
			gp.write("\n");
		}
		
		gp.close();
	}
	
	// Win Probability Function
	public static double WP (double rating1, double rating2, double vol1, double vol2) {
		double plugIntoErf = (rating1 - rating2) / Math.pow(2 * (Math.pow(vol1, 2) + Math.pow(vol2, 2)), 0.5);
		return 0.5 * (Erf.erf(plugIntoErf) + 1);
	}
	
	// Expected Rank
	public static double ERank(Coder[] clist, int index) {
		double ret = 0.5;
		for(int i=0; i<clist.length; i++) {
			ret += WP(clist[i].rating, clist[index].rating, clist[i].volatility, clist[index].volatility);
		}
		return ret;
	}
	
	// Performance Calc
	public static double Perf(double rank, int num) {
		return -1 * inverseCumProb((rank - 0.5) / ((double)num));
	}
	
	// Performed As
	public static double PerfAs(double oldRating, double cf, double aperf, double eperf) {
		return oldRating + cf * (aperf - eperf);
	}
	
	// Weight
	public static double Weight(int timesPlayed) {
		double temp = 0.42 / ((double) timesPlayed + 1.0);
		temp += 0.18;
		temp = 1 - temp;
		temp = 1 / temp;
		return temp - 1;
	}
	
	// Cap
	public static double Cap(int timesPlayed) {
		return 150 + (1500/ ((double) timesPlayed + 2));
	}
	
	// New Rating
	public static double NewRating(double oldrating, double weight, double perfas, double cap) {
		double newrating = (oldrating + weight * perfas)/(1.0 + weight);
		if(newrating - oldrating > cap) {
			return oldrating + cap;
		} else if (oldrating - newrating > cap) {
			return oldrating - cap;
		} else {
			return newrating;
		}
	}
	
	// New Volatility
	public static double NewVolatility(double newrating, double oldrating, double weight, double oldvolatility) {
		double temp = Math.pow((newrating - oldrating), 2)/weight + Math.pow(oldvolatility, 2)/(weight + 1);
		temp = Math.pow(temp, 0.5);
		return temp;
	}
	
	// Grand Prix Calculation
	public static double GPCalc(double rating, int attended, int mostRecent) {
		double preBonus = rating * ((LOGISTIC_CONSTANT / (1 + Math.exp(-1.0 * K_CONSTANT * attended))) - (LOGISTIC_CONSTANT / 2));
		double points = preBonus;
		for(int i=0; i<mostRecent; i++) {
			points += WEEKLY_BONUS[i];
		}
		return points;
	}
	
	// Inverse Cumulative Probability Function
    public static double inverseCumProb(double p) {
    	return 1 * Math.pow(2, 0.5) * Erf.erfInv(2 * p - 1);
    }
    
}

class Coder {
	public String id;
	public double rating;
	public double volatility;
	public int num;
	public int recent;
	public double oldPoints;
	public double points;
	public Coder(String i, double r, double v, int n, int rec, double op, double p) {
		id = i;
		rating = r;
		volatility = v;
		num = n;
		recent = rec;
		oldPoints = op;
		points = p;
	}
}
