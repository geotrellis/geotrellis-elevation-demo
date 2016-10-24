"""
Downloads and preprocesses a set of elevation data from the USGS National Map (http://viewer.nationalmap.gov/)
Call with a path to the CSV exported from the national map containing the elevation data you'd like to work with.
"""
import argparse
import csv
import os
import shutil
import urllib.request
from subprocess import call
from multiprocessing import Pool

def download(tup):
    print("Downloading %s" % tup[0])
    file_url, save_path = tup
    call("wget %s -O %s" % (file_url, save_path), shell=True)

def download_all(files, download_dir):
    if not os.path.exists(download_dir):
        os.makedirs(download_dir)
    elif not os.path.isdir(download_dir):
        raise Exception("Download Directory is a file: %s" % download_dir)

    tups = []
    for file_url in files:
        save_path = os.path.join(download_dir, os.path.basename(file_url))
        tups.append((file_url, save_path))
    pool = Pool()
    pool.map(download, tups)

    return map(lambda x: x[1], tups)

def unpack(tup):
    source, target = tup
    call("unzip -d %s %s" % (target, source), shell=True)

def unpack_and_find_imgs(paths, working_dir):
    tups = []
    for path in paths:
        base = os.path.splitext(os.path.basename(path))[0]
        tups.append((path, "%s/%s" % (working_dir, base)))

    pool = Pool()
    pool.map(unpack, tups)

    imgs = []
    for root, folders, files in os.walk(working_dir):
        for f in files:
            if f.endswith('.img'):
                imgs.append(os.path.join(root, f))

    print(imgs)
    return imgs

def convert(tup):
    source, target = tup
    cmd = 'gdal_translate -of GTiff -co compress=deflate -co tiled=yes "%s" "%s"' % (source, target)
    print("RUNNING %s" % cmd)
    call(cmd, shell=True)
    print("PROCESSED %s" % target)

def convert_all(paths, working_dir):
    tups = []
    for path in paths:
        base = os.path.splitext(os.path.basename(path))[0]
        tiff_path = os.path.join(working_dir, base + ".tif")
        tups.append((path, tiff_path))

    pool = Pool(processes=4)
    pool.map(convert, tups)

    return map(lambda x: x[1], tups)

def move_tiffs(output_path, paths):
    if not os.path.exists(output_path):
        os.makedirs(output_path)
    elif not os.path.isdir(output_path):
        raise Exception("Output Path is a file: %s" % output_path)
    result = []
    for path in paths:
        new_path = os.path.join(output_path, os.path.basename(path))
        shutil.move(path, new_path)
        result.append(new_path)

    return result


def process(path_to_csv, output_path, download_dir, cleanup, skip_download):
    working_dir = "temp-working-dir"
    if not os.path.exists(working_dir):
        os.mkdir(working_dir)

    if not download_dir:
        download_dir = working_dir

    try:
        remote_files = []
        with open(path_to_csv) as csvfile:
            reader = csv.DictReader(csvfile)
            for row in reader:
                remote_files.append(row['downloadURL'])

        local_zips = None
        if skip_download:
            local_zips = []
            for fname in os.listdir(download_dir):
                if fname.endswith('.zip'):
                    local_zips.append(os.path.join(download_dir, fname))
        else:
            local_zips = download_all(remote_files, download_dir)
        local_imgs = unpack_and_find_imgs(local_zips, working_dir)
        local_tiffs = convert_all(local_imgs, working_dir)
        return move_tiffs(output_path, local_tiffs)
    finally:
        if cleanup:
            shutil.rmtree(working_dir)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description='Download and process elevation data.')
    parser.add_argument('path', metavar='PATH',
                        help='Path to the CSV exported from the USGS national map containing the elevation data to process')
    parser.add_argument('-o', '--output', help='Output directory in which to place GeoTiffs')
    parser.add_argument('-n', '--no_cleanup', help='Do not cleanup working directory', action='store_true')
    parser.add_argument('-d', '--download_dir', help='Directory to download files into. If not specified, downloaded files will saved to and deleted with the working directory')
    parser.add_argument('--skip_download', help='Skip the download step', action='store_true')

    args = parser.parse_args()

    output = '.'
    if args.output:
        output = args.output

    for p in process(args.path, output, args.download_dir, not args.no_cleanup, args.skip_download):
        print(p)
