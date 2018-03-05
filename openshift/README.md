# Install on OpenShift with EnMasse

This document requires a dedicated OpenShift 3.7 cluster setup.

Things to do:

 - [X] Separate EnMasse from Hono project/namespace
 - [X] Separate Grafana from Hono project/namespace
 - [ ] Enable Grafana "Guest"
 - [ ] Switch to Grafana 5.0 (when it is released)
 - [ ] Enable datasource provisioning (requires Grafana 5.0)
 - [ ] Enable dashboard provisioning (requires Grafana 5.0)

## Deploy EnMasse

Login in to OpenShift and create a new EnMasse project:

~~~sh
oc login
oc new-project enmasse --display-name='EnMasse Instance'
~~~

Download and unpack EnMasse:

~~~sh
wget https://github.com/EnMasseProject/enmasse/releases/download/0.17.0/enmasse-0.17.0.tgz
tar xzf enmasse-0.17.0.tgz
cd enmasse-0.17.0
~~~

Deploy EnMasse:

~~~sh
./deploy-openshift.sh -n enmasse -m https://my-cluster:8443 -u developer
~~~

## Create PVs

You need to create a set of PVs. Grafana, InfluxDB and the Hono Device registry need a persistent storage.

**Note:** Creating PVs is a cluster admin task. You will need cluster admin privileges for your user or local cluster access.

This setup provides examples for two storage types: NFS and local disk. The examples are located
in the directory [admin/](admin/). You only need to create one type.

You need to *adapt* the files to your needs (change NFS server, paths) and execute them like this:

~~~sh
oc create -f admin/hono-pv-nfs.yml
oc create -f admin/grafana-pv-nfs.yml
…
~~~

**Note:** When using minishift you can ignore this step as minishift will automatically allocate PVs for you.
**Note:** Local disk PVs only work in a single node OpenShift setup.

Also see:

* [OpenShift: Persistent Storage](https://docs.openshift.org/latest/architecture/additional_concepts/storage.html)

## Create configuration in EnMasse

~~~sh
PROJECT=enmasse
curl -X PUT --insecure -T "addresses.json" -H "content-type: application/json" https://$(oc -n "$PROJECT" get route restapi -o jsonpath='{.spec.host}')/apis/enmasse.io/v1/addresses/default
~~~

## Deploy Hono template

Create a new project for Hono:

~~~sh
oc new-project hono --display-name='Eclipse Hono™'
~~~

Create the InfluxDB ConfigMap from a local file:

~~~sh
oc -n hono create configmap influxdb-config --from-file="../example/src/main/config/influxdb.conf"
~~~

The Hono template is located at: [hono.yml](hono.yml). It needs to be processed and executed.

This can be done from the command line like this:

~~~sh
oc process -f hono.yml | oc create -f -
~~~

You can set template parameters like that:

~~~sh
oc process -f hono.yml \
   -p "ENMASSE_NAMESPACE=enmasse" \
   -p "GIT_REPOSITORY=https://github.com/ctron/hono" \
   -p "GIT_BRANCH=feature/support_s2i_05x"| oc create -f -
~~~

**Note:** Currently you will need to set the following parameters:

* `ENMASSE_NAMESPACE=enmasse`
* `GIT_REPOSITORY=https://github.com/ctron/hono`
* `GIT_BRANCH=feature/support_s2i_05x`

See also:
* [OpenShift: Templates](https://docs.openshift.org/latest/dev_guide/templates.html) 

## Deploy Grafana template

Create a new project for Grafana:

~~~sh
oc new-project grafana --display-name='Grafana Dashboard'
~~~

The Grafana template is located at: [grafana.yml](grafana.yml). It needs to be processes and executed.
Please see [Deploy Hono template](#deploy-hono-template) for more information about deploying a
template.

~~~sh
oc process -f grafana.yml | oc create -f -
~~~

If you want to stick to the default "admin" password use:

~~~sh
oc process -f grafana.yml -p ADMIN_PASSWORD=admin | oc create -f -
~~~
