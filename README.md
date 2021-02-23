Code forked for the Jetbrains Machine Learning challenge.

<img src="images/Header.png" width="800" alt="LambdaNet Header"/>

This is the source code repo for the ICLR paper [*LambdaNet: Probabilistic Type Inference using Graph Neural Networks*](https://openreview.net/forum?id=Hkx6hANtwH). For an overview of how LambdaNet works, see [our video from ICLR 2020](https://iclr.cc/virtual_2020/poster_Hkx6hANtwH.html).

## Instructions
After cloning this repo, here are the steps to reproduce our experimental results:

 1. Install all the dependencies (Java, sbt, Typescript, etc.) See the "Using Docker" section below.
 2. To run pre-trained model
    1. download the model weights using [this link (predicts user defined type)](https://drive.google.com/file/d/1NvEVQ4-5tC3Nc-Mzpu3vYeyEcaM_zEgV/view?usp=sharing) or [this link (only library types)](TODO), unzip the file, and put the `models` file under the project root. 
    2. To run the model in interative mode, which outputs `(source code position, predicted type)` pairs for the specified files:
        1. Under project root, run `sbt "runMain lambdanet.TypeInferenceService"`.
    2. Alternatively, to run the model in batched mode, which outputs human-readable HTML files and accuracy statistics:
        1. download the [parsedRepos file](https://drive.google.com/file/d/1ZhsUf9bUzT3ZJB0KzNP6w2aj3sQZwtsp/view?usp=sharing), unzip the file and put the directory under `<project root>/data`.
        2. Check the file `src/main/scala/lambdanet/RunTrainedModel.scala` and change the parameters under the todo comments depending on which model you want to run and where your test TypeScript files are located. (Currently, LambdaNet only works with Typescript files, so if you want to run it on Javascript files, you will need to change the file extensions to `.ts`.)
        3. Under project root, use `sbt runTrained` to run the model.
 3. To train LambdaNet from scratch 
    1. Download the Typescript projects used in our experiments.
    2. Filter and prepare the TS projects into a serialization format.
    3. start the training.

The Typescript files used for manual comparison with JSNice are put under the directory `data/comparison/`.


### Using Docker
We also provide a Docker file to automatically download and install all the dependencies. Here are the steps to run pre-trained LambdaNet model inside a Docker Container: 

  1. First, make sure you have [installed Docker](https://www.docker.com/get-started).
  
  2. Put pre-trained model weights under `models/`.
   
  3. Under project root, run `docker build -t lambdanet:v1 .
  && docker run --name lambdanet --memory 14g -t -i lambdanet:v1 `. (Make sure the machine you are using has enough memory for the `docker run` command.)
  
  4. After the Docker container has successfully started, run `sbt runTrained`, and you should see LambdaNet outputs "libAccuracy" and "projectAccuracy" after a few minutes. LambdaNet also stores its predictions into an Html file under `<test TS project>/predictions/` (`<test TS project>` is currently default to `data/ts-algorithms`, but you can change this in `src/main/scala/lambdanet/RunTrainedModel.scala`.)

Java CLI
------
Be sure to follow the steps descrived above before running the Java CLI.

### How to run
To run the Java CLI, run `sbt "runMain cli.JavaCLI models/newParsing-GAT1-fc2-newSim-decay-6 data/parsedRepos data/ts-algorithms"`. The first argument is the pre-trained model path, the second is the parse from file path, and the last the input project directory.

The command will print in the stdout the predictions for the input project. A large window width is recommended for a better output visualization.

### How to create a FAT JAR

To create a fat jar with all dependencies, run `sbt package`.
