Pipeline Agent Build History
==========

The classical `Build History` for an agent that is available in Jenkins core is not able to show runs of Pipeline jobs
that have been executed on an agent. This plugin fills this gap by providing an additional link on an agents side panel
opening a view that shows runs of pipeline jobs but also all the other job types that are available in the original
`Build History`.

For Pipeline runs the status is the overall status. So if the pipeline failed it doesn't mean that this was something
that was executed on that specific agent. It can also be due to a step on another agent or a step outside a node step.

A button is added behind the links to the run that allows to show all node steps executed on the agent. If any step
inside the node steps closure failed the status is set to `Failure` and highlighted in red.
 
Looking at the output of this simple pipeline, this will produce on the agent `mynode` the below lines
```
node('mynode') {
  sh 'Echo Hello' 
}
node('othernode') {
  sh 'echo 
}
node('mynode') {
  sh '''
    echo Hello
    exit 1
  ''' 
}
```
![img.png](docs/img.png)
