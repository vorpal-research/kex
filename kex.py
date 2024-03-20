#!/bin/python3

import os.path
import re
import subprocess
import sys

KEX_VERSION = "0.0.2"
HEAP_MEMORY_SIZE = "8g"
STACK_MEMORY_SIZE = "1g"

kex_home = os.path.dirname(os.path.realpath(__file__))
MODULES_FILE = os.path.join(kex_home, "runtime-deps", "modules.info")


def get_jvm_version() -> int:
	pattern = '\"(1.)?(\d+).*\"'
	runner = subprocess.Popen(
		["java", "-version"],
		stdout=subprocess.PIPE,
		stderr=subprocess.STDOUT
	)
	stdout, stderr = runner.communicate()
	version = re.search(pattern, stdout.decode()).groups()[1]
	return int(version)

def get_jvm_args() -> list [str]:
	version = get_jvm_version()
	if version < 8:
		print("Only Java version 8+ is supported", file=sys.stderr)
		quit()
	elif version == 8:
		return []
	else:
		modules = open(MODULES_FILE).read()
		args = []
		for line in modules.splitlines():
			args.append("--add-opens")
			args.append(line)
		args.append("--illegal-access=warn")
		return args

def run_kex(args: list [str]):
	kex_env = os.environ.copy()
	kex_env["KEX_HOME"] = kex_home
	command = ["java",
		"-Xmx{}".format(HEAP_MEMORY_SIZE),
		"-Djava.security.manager",
		"-Djava.security.policy=={}".format(os.path.join(kex_home, "kex.policy")),
		"-Dlogback.statusListenerClass=ch.qos.logback.core.status.NopStatusListener"
	]
	command.extend(get_jvm_args())
	command.extend([
		"-jar", os.path.join(kex_home, "kex-runner", "target", "kex-runner-{}-jar-with-dependencies.jar".format(KEX_VERSION))
	])
	command.extend(args)
	kexProcess = subprocess.Popen(command, env=kex_env)
	kexProcess.wait()
	return kexProcess.returncode

def main() -> int:
	return run_kex(sys.argv[1:])

if __name__ == "__main__":
	returnCode = main()
	sys.exit(returnCode)
