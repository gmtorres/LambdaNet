package cli;


import lambdanet.TypeInferenceService;
import lambdanet.TypeInferenceService$;

import lambdanet.translation.PredicateGraph;
import lambdanet.train.TopNDistribution;

import lambdanet.package$;

import scala.collection.JavaConverters;
import scala.Tuple2;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.Comparator;

import java.util.Collection;
import java.util.Collections;


public class JavaCLI{

    public static void main(String [] args){

        if(args.length != 3){
            System.out.println("Bad argument usage : <model> <parseFromFile> <inputFile> ");
            System.out.println("<model> : path to a pre-trained LambdaNet model");
            System.out.println("<parseFromFile> : path to the parsingFromFile.ts");
            System.out.println("<inputFile> : path to input TypeScript file");
            System.exit(-1);
        }
        String modelPath = args[0];
        String parseFromFilePath = args[1];
        String inputFilePath = args[2];

        var api = lambdanet.JavaAPI$.MODULE$;
        var typeInfer = TypeInferenceService$.MODULE$;
        var workDir = api.pwd();

        System.out.println("JavaCLI started.");

        var modelDir = api.joinPath(workDir,modelPath);
        var paramPath = api.joinPath(modelDir, "params.serialized");
        var modelCachePath = api.joinPath(modelDir, "model.serialized");
        var modelConfig = api.defaultModelConfig();

        var parsedReposDir = api.joinPath(workDir, parseFromFilePath);

        var model = typeInfer.loadModel(paramPath, modelCachePath, modelConfig, 8, parsedReposDir);
        var predService = api.predictionService(model, 8, 5);
        //System.out.println(model);
        System.out.println("Type Inference Service successfully started.");
        System.out.println("Current working directory: " + workDir);

        System.out.println(modelConfig);

        var sourcePath = inputFilePath.startsWith("/") ?
                        api.absPath(inputFilePath) :
                        api.joinPath(workDir, inputFilePath);
        try {
            String[] skipSet = {"node_modules"};
            var results =
                    predService.predictOnProject(sourcePath, true, skipSet);
            //new TypeInferenceService.PredictionResults(results).prettyPrint();
            printPredictions(results);
            System.out.println("JavaCLI done.");
        }catch (Throwable e) {
            System.out.println("Got exception: " + e.getMessage());
            e.printStackTrace();
        }

    }

    private static void printPredictions(
        scala.collection.immutable.Map<
            PredicateGraph.PNode, 
            TopNDistribution<PredicateGraph.PType
        >> results
    ){  
        Map<
            PredicateGraph.PNode, 
            TopNDistribution<PredicateGraph.PType>
        > java_map = scala.collection.JavaConverters.mapAsJavaMapConverter(results).asJava();
        Set<PredicateGraph.PNode> set = java_map.keySet();
        HashMap<String,ArrayList<PredicateGraph.PNode>> files = new HashMap<>();

        for(PredicateGraph.PNode pnode : java_map.keySet()){
            String file_name = pnode.srcSpan().get().srcFile().toString();
            if(!files.containsKey(file_name))
                files.put(file_name, new ArrayList<PredicateGraph.PNode>());
            files.get(file_name).add(pnode);
        }

        for(Map.Entry<String,ArrayList<PredicateGraph.PNode>> entry: files.entrySet()){
            String file = entry.getKey();
            ArrayList<PredicateGraph.PNode> nodes = entry.getValue();
            class SortNodes implements Comparator<PredicateGraph.PNode>{ 
                public int compare(PredicateGraph.PNode node1, PredicateGraph.PNode node2){
                    var line_1 = (Integer) node1.srcSpan().get().start()._1();
                    var line_2 = (Integer) node2.srcSpan().get().start()._1();
                    if(line_1 - line_2 == 0){
                        var start_1 = (Integer) node1.srcSpan().get().start()._2();
                        var start_2 = (Integer) node2.srcSpan().get().start()._2();
                        return start_1 - start_2;
                    }
                    return line_1-line_2;
                }
            };  
            Collections.sort(nodes, new SortNodes());
            System.out.println(" --------- File:" + file + " --------- ");
            String suggestions_str = "";
            for(int i = 0; i < 5;i++){
                String temp = i == 0 ? "Top Suggestion" : "Suggestion #" + (i+1);
                suggestions_str += String.format("%1$22s", temp) + "\t";
            }
            System.out.println(String.format("%1$16s", "Name") + "   " +
                                String.format("%1$16s", "line:(start,end)") + "\t" +
                                suggestions_str );
            for(PredicateGraph.PNode node : nodes){
                String name = node.nameOpt().isDefined() ? node.nameOpt().get().name() : "";
                String name_format = String.format("%1$16s", name);
                var srcSpan = node.srcSpan().get();
                TopNDistribution<PredicateGraph.PType> top = java_map.get(node);
                Collection top_collection 
                    = scala.collection.JavaConverters.asJavaCollectionConverter(top.distr()).asJavaCollection();
                ArrayList<scala.Tuple2<Double,PredicateGraph.PType>> top_list = new ArrayList<scala.Tuple2<Double,PredicateGraph.PType>>(top_collection);
                
                System.out.print(name_format + " - ");
                String position = ""; 
                position += srcSpan.start()._1() + ":(" + srcSpan.start()._2() + "," + srcSpan.until()._2() + ")";
                String position_format = String.format("%1$16s", position);
                System.out.print(position_format + "\t");
                Double power = 100.0;
                for(int i = 0; i < top_list.size();i++){
                    scala.Tuple2<Double,PredicateGraph.PType> tuple = top_list.get(i);
                    Double prob = Math.round(tuple._1() * 100 * power) / power;
                    PredicateGraph.PType type_name = tuple._2();
                    String type_str = "";
                    type_str += type_name.showSimple() + "(" + prob + "%)";
                    String type_str_format = String.format("%1$22s", type_str);
                    System.out.print(type_str_format + "\t");
                }
                System.out.println("");
            }
        }
        System.out.println("");

    }

}