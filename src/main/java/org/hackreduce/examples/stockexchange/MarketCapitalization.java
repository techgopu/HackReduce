package org.hackreduce.examples.stockexchange;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.Locale;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.hackreduce.mappers.StockExchangeMapper;
import org.hackreduce.models.StockExchangeRecord;


/**
 * This MapReduce job will read the NASDAQ or NYSE daily prices dataset and output the highest market caps
 * obtained by each Stock symbol.
 *
 */
public class MarketCapitalization extends Configured implements Tool {

	public enum Count {
		STOCK_SYMBOLS
	}

	public static class MarketCapitalizationMapper extends StockExchangeMapper<Text, DoubleWritable> {

		@Override
		protected void map(StockExchangeRecord record, Context context) throws IOException, InterruptedException {
			double marketCap = record.getStockPriceClose() * record.getStockVolume();
			context.write(new Text(record.getStockSymbol()), new DoubleWritable(marketCap));
		}

	}

	public static class MarketCapitalizationReducer extends Reducer<Text, DoubleWritable, Text, Text> {

		NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.getDefault());

		@Override
		protected void reduce(Text key, Iterable<DoubleWritable> values, Context context) throws IOException, InterruptedException {
			context.getCounter(Count.STOCK_SYMBOLS).increment(1);

			double highestCap = 0.0;
			for (DoubleWritable value : values) {
				highestCap = Math.max(highestCap, value.get());
			}

			context.write(key, new Text(currencyFormat.format(highestCap)));
		}

	}

	@Override
	public int run(String[] args) throws Exception {
        Configuration conf = getConf();

        if (args.length != 2) {
        	System.err.println("Usage: " + getClass().getName() + " <input> <output>");
        	System.exit(2);
        }

        // Creating the MapReduce job (configuration) object
        Job job = new Job(conf);
        job.setJarByClass(getClass());
        job.setJobName(getClass().getName());

        // Tell the job which Mapper and Reducer to use (classes defined above)
        job.setMapperClass(MarketCapitalizationMapper.class);
		job.setReducerClass(MarketCapitalizationReducer.class);

		// Configure the job to accept the NASDAQ/NYSE data as input
		StockExchangeMapper.configureJob(job);

		// This is what the Mapper will be outputting to the Reducer
		job.setMapOutputKeyClass(Text.class);
		job.setMapOutputValueClass(DoubleWritable.class);

		// This is what the Reducer will be outputting
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(Text.class);

		// Setting the input folder of the job 
		FileInputFormat.addInputPath(job, new Path(args[0]));

		// Preparing the output folder by first deleting it if it exists
        Path output = new Path(args[1]);
        FileSystem.get(conf).delete(output, true);
	    FileOutputFormat.setOutputPath(job, output);

		return job.waitForCompletion(true) ? 0 : 1;
	}

	public static void main(String[] args) throws Exception {
		int result = ToolRunner.run(new Configuration(), new MarketCapitalization(), args);
		System.exit(result);
	}

}
