import groovy.xml.MarkupBuilder

pipeline {
    agent { label "Debug_win" }

    stages {
        stage('Checking out sources') {
            steps {
                checkout changelog: false, poll: false, scm: [$class: 'GitSCM', branches: [[name: 'refs/heads/release_oneDPL']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'ci']], submoduleCfg: [], userRemoteConfigs: [[url: 'https://github.com/oneapi-src/oneDPL.git']]]
                echo 'Done'
            }
		}
        stage('Setting up onaAPI environment') {
            steps {
				script {
					output = bat returnStdout: true, script: '@call "C:\\Program Files (x86)\\Intel\\oneAPI\\setvars.bat" > NUL && set'
					oneapi_env = output.split('\r\n') as List
				}
            }
        }
        stage('Running tests') {
            steps {
				script {
					//def tests = findFiles glob: 'ci/test/pstl_testsuite/pstl/**/*pass.cpp'
					def tests = findFiles glob: 'ci/test/pstl_testsuite/**/*pass.cpp' //uncomment this line to run all tests
					echo tests.toString()
					def failCount = 0
					def passCount = 0
					def results = []

					withEnv(oneapi_env) {
						for ( x in tests ) {
							try {
								phase = 'Build'
								bat "dpcpp /W0 /nologo /D _UNICODE /D UNICODE /Zi /WX- /EHsc /Fetest.exe /Ici/include /Ici/test/pstl_testsuite $x"
								phase = 'Run'
								bat "test.exe"
								echo 'PASS'
								passCount++
								results.add([name: x, pass: true, phase: phase])
							}
							catch (e) {
								echo 'FAIL'
								failCount++
								results.add([name: x, pass: false, phase: phase])
							}
						}
					}
					xml = write_results_xml(results)
					writeFile file: 'report.xml', text: xml
					echo "Passed tests: $passCount, Failed tests: $failCount"
					if (failCount > 0) {
						bat 'exit 1'
					}
				}
            }
        }
    }
	post {
		always {
			junit 'report.xml'
		}
	}
}

@NonCPS
def write_results_xml(results) {
	def xmlWriter = new StringWriter()
	def xml = new MarkupBuilder(xmlWriter)
	xml.testsuites{
	    delegate.testsuite(name: 'tests') {
    		results.each { item ->
				if (item.pass) {
					delegate.delegate.testcase(name: item.name, classname: item.name)
				}
				else {
					delegate.delegate.testcase(name: item.name, classname: item.name) {
						delegate.failure(message: 'Fail', item.phase)
					}
				}
    		}
		}
	}
	return xmlWriter.toString()
}
