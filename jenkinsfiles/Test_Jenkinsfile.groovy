
def fill_task_name_description () {
    script {
        currentBuild.displayName = "PR#${env.PR_number}-No.${env.BUILD_NUMBER}"
        currentBuild.description = "PR number: ${env.PR_number} / Commit id: ${env.Commit_id}"
    }
}
 
build_ok = true
fail_stage = ""
tests_branch_for_cts_vec = "cts_vec"

author_email = "None"

pipeline {

    //agent { label "Debug_RHEL" }
    agent { label "master" }
    options {
        durabilityHint 'PERFORMANCE_OPTIMIZED'
        timeout(time: 20, unit: 'HOURS')
        timestamps()
    }

    environment {
        def NUMBER = sh(script: "expr ${env.BUILD_NUMBER}", returnStdout: true).trim()
        def TIMESTEMP = sh(script: "date +%s", returnStdout: true).trim()
        def DATESTEMP = sh(script: "date +\"%Y-%m-%d\"", returnStdout: true).trim()
    }

    parameters {
        string(name: 'Commit_id', defaultValue: 'None', description: '',)
        string(name: 'PR_number', defaultValue: 'None', description: '',)
        string(name: 'Repository', defaultValue: 'DoyleLi/llvm', description: '',)
        string(name: 'Tools_branch', defaultValue: 'master', description: '',)
        string(name: 'User', defaultValue: 'bb-sycl', description: '',)
        booleanParam(name: 'Force_Check', defaultValue: false, description: 'Force Check regression code if set (ignore success history of each test).',)
    }

    triggers {
        GenericTrigger(
                genericVariables: [
                        [key: 'Commit_id', value: '$.pull_request.head.sha', defaultValue: 'None'],
                        [key: 'PR_number', value: '$.number', defaultValue: 'None'],
                        [key: 'Repository', value: '$.pull_request.base.repo.full_name', defaultValue: 'None'],
                        [key: 'User', value: '$.pull_request.user.login', defaultValue: 'None'],
                        [key: 'action', value: '$.action', defaultValue: 'None']
                ],

                causeString: 'Triggered on $PR_number',

//                token: 'doyleLi-test-pullrequest',
                token: 'doyle-li-test-sed',

                printContributedVariables: true,
                printPostContent: true,

                silentResponse: false,

                regexpFilterText: '$action',
                regexpFilterExpression: '(opened|reopened|synchronize)'
        )
    }

    stages {
        stage('Check_User_in_Org') {
            agent {
                label "master"
            }
            steps {
                script {
                    try {
//                        checkout changelog: false, poll: false, scm: [$class: 'GitSCM', branches: [[name: 'master']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'llvm_ci']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: '892534cc-be1b-4775-aa9c-da91fa162549', url: 'https://gitlab.devtools.intel.com/icl-qa-tools/onedpl_ci.git']]]

                        retry(2) {
                            def check_user_return = sh(script: "python3 /localdisk2/sam/check_user_in_group.py -u  ${env.User}", returnStatus: true, label: "Check User in Group")
                            echo "check_user_return value is $check_user_return"
                            if (check_user_return == 0) {
                                user_in_github_group = true
                            }
                            else {
                                user_in_github_group = false
                                currentBuild.result = 'UNSTABLE'
                            }
                        }
                    }
                    catch (e) {
                        fail_stage = fail_stage + "    " + "Check_User_in_Org"
                        user_in_github_group = false
                        echo "Exception occurred when check User:${env.User} in group. Will skip build this time"
                        sh script: "exit -1", label: "Set Failure"
                    }
                }
            }
        }

        stage('Print Hello') {
            when {
                expression { user_in_github_group }
            }
            parallel {
                stage('RHEL'){
                    agent { label "Debug_RHEL" }
                    steps {
                        script {
                            try {
                                retry(2) {
                                    fill_task_name_description()
                                    echo "helloworld on RHEL"
                            	    checkout changelog: false, poll: false, scm: [$class: 'GitSCM', branches: [[name: 'master']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'llvm_ci']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: '9d434875-1c6b-4745-924b-52ed38305a9f', url: 'https://github.com/otcshare/llvm_ci.git']]]

                                    
                                }
                            }
                            catch (e) {
                                fail_stage = fail_stage + "    " + "RHEL"
                                catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                                    sh script: "exit 1"
                                }
                            }
                        }
                    }
                }
                stage('Ubuntu'){
                    agent { label "Debug_Ubuntu" }
                    steps {
                        script {
                            try {
                                retry(2) {
                                    
                                    echo "helloworld on Ubuntu"
                            	    checkout changelog: false, poll: false, scm: [$class: 'GitSCM', branches: [[name: 'master']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'llvm_ci']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: '9d434875-1c6b-4745-924b-52ed38305a9f', url: 'https://github.com/otcshare/llvm_ci.git']]]
        
                                    
                                }
                            }
                            catch (e) {
                                fail_stage = fail_stage + "    " + "Ubuntu"
                                catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                                    sh script: "exit 1"
                                }
                            }
                        }
                    }
                }
            }
            
            
        }

    }

}
