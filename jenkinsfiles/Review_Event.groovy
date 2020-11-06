

def fill_task_name_description () {
    script {
        currentBuild.displayName = "PR#${env.PR_number}-No.${env.BUILD_NUMBER}"
        currentBuild.description = "PR number: ${env.PR_number} / Commit id: ${env.Commit_id}"
    }
}

build_ok = true
fail_stage = ""
user_in_github_group = false

pipeline {

    agent { label "master" }
    options {
        durabilityHint 'PERFORMANCE_OPTIMIZED'
        timeout(time: 5, unit: 'HOURS')
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
    }

    triggers {
        GenericTrigger(
                genericVariables: [
                        [key: 'Commit_id', value: '$.pull_request.head.sha', defaultValue: 'None'],
                        [key: 'PR_number', value: '$.pull_request.number', defaultValue: 'None'],
                        [key: 'Repository', value: '$.pull_request.base.repo.full_name', defaultValue: 'None'],
                        [key: 'review_state', value: '$.review.state', defaultValue: 'None'],
                        [key: 'User', value: '$.review.user.login', defaultValue: 'None']
                ],

                causeString: 'Triggered on $PR_number',

                token: 'oneDPL-pre-ci-review',

                printContributedVariables: true,
                printPostContent: true,

                silentResponse: false,

                regexpFilterText: '$review_state',
                regexpFilterExpression: 'approved'
        )
    }

    stages {
        stage('Check_And_Launch_CI') {
            agent {
                label "master"
            }
            steps {
                script {
                    try {
                        retry(2) {
                            fill_task_name_description()

                            def windows_check_value = sh script: """
                                                        python3 /localdisk2/oneDPL_CI/check_build_executed.py -c  ${env.Commit_id} -n "Jenkins/Win_Check"
                                                     """, returnStatus: true, label: "Check precommit CI has built"
                            echo "windows_check_value value is $windows_check_value"
                            if (windows_check_value != 0) {
                                buildInfo = build job: 'Windows_Check', parameters: [string(name: 'Commit_id', value: "${Commit_id}"), string(name: 'PR_number', value: "${env.PR_number}"), string(name: 'Repository', value: "${env.Repository}"), string(name: 'User', value: "${env.User}")], propagate: false, wait: false
                            }

                            def RHEL_check_value = sh script: """
                                                        python3 /localdisk2/oneDPL_CI/check_build_executed.py -c  ${env.Commit_id} -n "Jenkins/RHEL_Check"
                                                     """, returnStatus: true, label: "Check precommit CI has built"
                            echo "RHEL_check_value value is $RHEL_check_value"
                            if (RHEL_check_value != 0) {
                                buildInfo = build job: 'RHEL_Check', parameters: [string(name: 'Commit_id', value: "${Commit_id}"), string(name: 'PR_number', value: "${env.PR_number}"), string(name: 'Repository', value: "${env.Repository}"), string(name: 'User', value: "${env.User}")], propagate: false, wait: false
                            }

                            def UB1804_check_value = sh script: """
                                                        python3 /localdisk2/oneDPL_CI/check_build_executed.py -c  ${env.Commit_id} -n "Jenkins/UB1804_Check"
                                                     """, returnStatus: true, label: "Check precommit CI has built"
                            echo "UB1804_check_value value is $UB1804_check_value"
                            if (UB1804_check_value != 0) {
                                buildInfo = build job: 'UB1804_Check', parameters: [string(name: 'Commit_id', value: "${Commit_id}"), string(name: 'PR_number', value: "${env.PR_number}"), string(name: 'Repository', value: "${env.Repository}"), string(name: 'User', value: "${env.User}")], propagate: false, wait: false
                            }

                            def UB20_check_value = sh script: """
                                                        python3 /localdisk2/oneDPL_CI/check_build_executed.py -c  ${env.Commit_id} -n "Jenkins/UB20_Check"
                                                     """, returnStatus: true, label: "Check precommit CI has built"
                            echo "UB20_check_value value is $UB20_check_value"
                            if (UB20_check_value != 0) {
                                buildInfo = build job: 'UB20_Check', parameters: [string(name: 'Commit_id', value: "${Commit_id}"), string(name: 'PR_number', value: "${env.PR_number}"), string(name: 'Repository', value: "${env.Repository}"), string(name: 'User', value: "${env.User}")], propagate: false, wait: false
                            }

                            if (windows_check_value == 0 && RHEL_check_value == 0 && UB1804_check_value == 0 && UB20_check_value ==0) {
                                currentBuild.result = 'UNSTABLE'
                            }
                        }
                    }
                    catch (e) {
                        fail_stage = fail_stage + "    " + "Check_Before_Launch_Jobs"
                        echo "Check the commit ${env.Commit_id} from PR:${env.PR_number} failed. Will not launch CI tasks."
                        sh script: "exit -1", label: "Set Failure"
                    }
                }
            }
        }
    }


}
