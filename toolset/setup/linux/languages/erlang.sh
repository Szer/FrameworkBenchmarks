#!/bin/bash

RETCODE=$(fw_exists ${IROOT}/erlang.installed)
[ ! "$RETCODE" == 0 ] || { return 0; }

export OTP_SRC="otp_src_17.5"
fw_get http://www.erlang.org/download/${OTP_SRC}.tar.gz
fw_untar ${OTP_SRC}.tar.gz

(
	cd $OTP_SRC
	export ERL_TOP=`pwd`
	./configure --prefix=$IROOT/erlang --without-termcap
	make
	make install
)

touch ${IROOT}/erlang.installed
