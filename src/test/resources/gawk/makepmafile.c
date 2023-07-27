/*
 * makepmafile.c -- Create a small sparse file.
 */

/*
 * Copyright (C) 2022, 2023,
 * the Free Software Foundation, Inc.
 *
 * This file is part of GAWK, the GNU implementation of the
 * AWK Programming Language.
 *
 * GAWK is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * GAWK is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA
 */

#include <stdio.h>
#include <errno.h>
#include <string.h>
#include <unistd.h>
#include <stdlib.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>

int
main(int argc, char **argv)
{
	size_t four_meg = 1024 * 1024 * 4;
	char c = 0;
	int fd = creat("test.pma", 0600);

	if (fd < 0) {
		fprintf(stderr, "%s: could not create test.pma: %s\n",
				argv[0], strerror(errno));
		exit(EXIT_FAILURE);
	}

	if (lseek(fd, four_meg - 1, SEEK_SET) < 0) {
		fprintf(stderr, "%s: lseek failed: %s\n",
				argv[0], strerror(errno));
		exit(EXIT_FAILURE);
	}

	if (write(fd, &c, 1) < 0) {
		fprintf(stderr, "%s: write failed: %s\n",
				argv[0], strerror(errno));
		exit(EXIT_FAILURE);
	}

	(void) close(fd);
	return EXIT_SUCCESS;
}
