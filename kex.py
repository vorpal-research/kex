import subprocess
import typing
import re
import sys
import os.path

KEX_VERSION = "0.0.1"
HEAP_MEMORY_SIZE = "8g"
STACK_MEMORY_SIZE = "1g"

MODULES_FILE = os.path.join("runtime-deps", "modules.info")


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
	command = ["java",
		"-Xmx{}".format(HEAP_MEMORY_SIZE),
		"-Djava.security.manager",
		"-Djava.security.policy==kex.policy",
		"-Dlogback.statusListenerClass=ch.qos.logback.core.status.NopStatusListener"
	]
	command.extend(get_jvm_args())
	command.extend([
		"-jar", os.path.join("kex-runner", "target", "kex-runner-{}-jar-with-dependencies.jar".format(KEX_VERSION))
	])
	command.extend(args)
	kexProcess = subprocess.Popen(command)
	kexProcess.wait()
	return kexProcess.returncode

def main() -> int:
	return run_kex(sys.argv[1:])

if __name__ == "__main__":
	returnCode = main()
	sys.exit(returnCode)
