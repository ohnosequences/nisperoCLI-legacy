# nisperoCLI

*nisperoCLI* is command line interface for nispero.

## Usage

```
Usage: nisperoCLI [options] <command> <file>|<autoScaligGroup>

  -t <value> | --tasks <value>
        file with tasks description
  <command>
        nisperoCLI command: deploy, undeploy, list, managerLog, check
  <file>|<autoScaligGroup>
        nispero config or auto scaling group name
```

## nisperoCLI commands

### deploy

This command allows you to create new instance of nispero system.
**deploy** command takes one argument — file name with *nispero* configuration. Example:
```
nisperoCLI deploy nispero.config
```

### list
This command print list of all running instances of *nispero* with their auto scaling groups. Example
```
nisperoCLI list
```

## undeploy

This command terminate instances of nispero instance and terminate all resources created by it (you can setup behavior termination using "deletion policy" section in *nispero* configuration). 

This command take one argument — name of corresponded auto scaling group or configuration that generated during deploying ("nispero.config.generated")
```
nisperoCLI undeploy nisperoWorkersGroup7
```

## check

*nispero* has some requirements for *workers* and *manager* instances AMIs.
First of all it should be linux-based AMI (currently tested only with Amazon Linux). Next the following packages should be installed:

* yum package manager
* JRE with java in $PATH
* python
* unzip

To be ensure that your AMI satisfy these requirements you should run ``check``` commands. Besides this quick check this command also try to solve tasks that described in tasks arguments in this machine.
This command takes two arguments:
* ```-t <tasks>``` — name of file with tasks descriptions
* ```<nispero.config>``` — file with *nispero* configuration.

Example of usage:
```
nisperoCLI check -t tasks nispero.config
```

> You should run this program with sudo if you using *scriptExecutor* with scripts that assume that root right privileges. 

## managerLog
Troubleshooting feature. This command print log of manager application. Example of usage:
```
nisperoCLI managerLog nispero.config
```