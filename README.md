# Burningwave Miscellaneous Services
<a href="https://www.burningwave.org">
<img src="https://raw.githubusercontent.com/burningwave/burningwave.github.io/main/logo.png" alt="logo.png" height="180px" align="right"/>
</a>

[![HitCount](https://www.burningwave.org/generators/generate-visited-pages-badge.php?)](https://www.burningwave.org#bw-counters)

A simple application with which is possible:
* to retrieve some statistical data showed at the url /miscellaneous-services/stats/artifact-download-chart of the application
* to generate the artifact downloads badge
* to generate the GitHub stars badge
* to retrieve all the data of the statistical page in JSon format via the Rest API. The documentation of this API is available at the URL /miscellaneous-services/api-docs.html of the application

## Deploy requirements

The application can be deployed on [**Heroku**](https://www.heroku.com), on [**Amazon AWS**](https://aws.amazon.com/) or any other PaaS. For Amazon AWS you can find the intallation commands in the file [install-on-aws.sh](https://github.com/burningwave/miscellaneous-services/blob/main/install-on-aws.sh).

By default the application uses the file system based cache but it is recommended to use the database based cache by setting the [**environment property**](https://devcenter.heroku.com/articles/config-vars) `CACHE_TYPE` to `Database based`: in this case it is required a [**PostgreSQL DBMS**](https://www.postgresql.org/) which on Heroku can be simply installed with the [**Heroku Postgres addon**](https://elements.heroku.com/addons/heroku-postgresql).

## Configuration

On Heroku the minimal configuration requires only to set the `NEXUS_CONNECTOR_GROUP_CONFIG` [**environment property**](https://devcenter.heroku.com/articles/config-vars) with the [Nexus](https://oss.sonatype.org/) credentials encoded as a basic token e.g.: assuming that the Nexus username is `burningwave` and the password is `pa55w0rd`, the basic token will be the base64 encoding of the string `burningwave:pa55w0rd` and the value of the environment property `NEXUS_CONNECTOR_GROUP_CONFIG` will be:

```json
{
    "connector": [{
            "authorization": {
                "token": {
                    "value": "YnVybmluZ3dhdmU6cGE1NXcwcmQ="
                }
            }
        }
    ]
}
```

<br>

If you need to set up multiple Nexus accounts or customize the color and the target of the links in the artifact download statistics page you can do the following:
```json
{
    "connector": [{
            "host" : "oss.sonatype.org",
            "authorization": {
                "token": {
                    "value": "YnVybmluZ3dhdmU6cGE1NXcwcmQ="
                }
            },
            "project": [{
                    "name": "org.burningwave",
                    "artifacts": [{
                            "name": "jvm-driver",
                            "color": "00ff00",
                            "site": "https://burningwave.github.io/jvm-driver/"
                        }, {
                            "name": "core",
                            "color": "e54d1d",
                            "site": "https://burningwave.github.io/core/"
                        }, {
                            "name": "graph",
                            "color": "f7bc12",
                            "site": "https://burningwave.github.io/graph/"
                        }, {
                            "name": "tools",
                            "color": "066da1",
                            "site": "https://burningwave.github.io/tools/"
                        }
                    ]
                }, {
                    "name": "com.github.burningwave",
                    "artifacts": [{
                            "name": "bw-core",
                            "alias": "core",
                            "color": "e54d1d",
                            "site": "https://burningwave.github.io/core/"
                        }, {
                            "name": "bw-graph",
                            "alias": "graph",
                            "color": "f7bc12",
                            "site": "https://burningwave.github.io/graph/"
                        }
                    ]
                }
            ]
        }, {
            "host" : "s01.oss.sonatype.org",
            "authorization": {
                "token": {
                    "value": "dG9vbGZhY3Rvcnk6cGE1NXcwcmQ="
                }
            },
            "project": [{
                    "name": "io.github.toolfactory",
                    "artifacts": [{
                            "name": "narcissus",
                            "color": "402a94",
                            "site": "https://toolfactory.github.io/narcissus/"
                        }, {
                            "name": "jvm-driver",
                            "color": "00ff00",
                            "site": "https://toolfactory.github.io/jvm-driver/"
                        }
                    ]
                }
            ]
        }
    ]
}
```
